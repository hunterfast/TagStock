package de.tagstock.server.dienst;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Haelt die App bereit, die die Geraete sich holen. Der Server laedt die
 * neueste Fassung selbst herunter - aber nur, wenn er sie nicht schon hat -
 * und behaelt die vorherige, damit ein Geraet zurueck kann, wenn mit der neuen
 * etwas nicht stimmt.
 *
 * <pre>
 * /data/app/aktuell/tagstock.apk + app.json
 * /data/app/vorher/tagstock.apk  + app.json
 * </pre>
 *
 * Vor jedem Wechsel legt der Server eine Sicherung der Datenbank an. Die passt
 * dann zu der Fassung, die bis dahin lief - wer zurueckgeht, hat auch die
 * passenden Daten.
 *
 * <p>Ob es eine neue Fassung gibt, erfaehrt die Wache aus
 * {@link Veroeffentlichungen} - und die fragt nur, wenn jemand wissen will, was
 * bereitliegt, hoechstens einmal je Woche. Von selbst laedt der Server nichts.</p>
 */
@Component
public class Appwache {

    private static final Logger LOG = LoggerFactory.getLogger(Appwache.class);
    private static final String APK = "tagstock.apk";
    private static final String ANGABEN = "app.json";
    public static final String AKTUELL = "aktuell";
    public static final String VORHER = "vorher";

    private final Path ordner;
    private final Path sicherungen;
    private final boolean holen;
    private final String token;
    private final JdbcTemplate jdbc;
    private final Veroeffentlichungen quelle;
    private final HttpClient client;

    private final ObjectMapper json = new ObjectMapper();
    private String letzteMeldung;
    /**
     * Eine Fassung, deren Download zuletzt scheiterte. Ohne ausdrueckliches
     * Nachsehen wird sie nicht erneut versucht - sonst ginge bei jeder Anfrage
     * wieder eine ganze APK ueber die Leitung.
     */
    private String gescheitertBei;

    public Appwache(JdbcTemplate jdbc, Veroeffentlichungen quelle,
                    @Value("${tagstock.app-ordner:/data/app}") String ordner,
                    @Value("${tagstock.sicherungen-ordner:/data/sicherungen}") String sicherungen,
                    @Value("${tagstock.app-holen:true}") boolean holen,
                    @Value("${tagstock.github-token:}") String token) {
        this.jdbc = jdbc;
        this.quelle = quelle;
        this.ordner = Paths.get(ordner).toAbsolutePath().normalize();
        this.sicherungen = Paths.get(sicherungen).toAbsolutePath().normalize();
        this.holen = holen;
        this.token = token == null ? "" : token.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    // ------------------------------------------------------------- Auskunft

    public Path datei(String fach) {
        return ordner.resolve(AKTUELL.equals(fach) ? AKTUELL : VORHER).resolve(APK);
    }

    /** Was liegt bereit? Fuer die App und die Startseite. */
    public Map<String, Object> stand() {
        Map<String, Object> ergebnis = new LinkedHashMap<>();
        ergebnis.put(AKTUELL, fach(AKTUELL));
        ergebnis.put(VORHER, fach(VORHER));
        ergebnis.put("holtSelbst", holen && !token.isEmpty());
        long geholt = quelle.geholtAm();
        if (geholt > 0) {
            ergebnis.put("nachgesehenAm", java.time.Instant.ofEpochMilli(geholt).toString());
        }
        if (letzteMeldung != null) {
            ergebnis.put("hinweis", letzteMeldung);
        }
        return ergebnis;
    }

    private Map<String, Object> fach(String name) {
        Path apk = ordner.resolve(name).resolve(APK);
        if (!Files.isRegularFile(apk)) {
            return null;
        }
        Map<String, Object> angaben = new LinkedHashMap<>();
        angaben.put("adresse", "/api/v1/app/" + name + "/" + APK);
        try {
            angaben.put("groesse", Files.size(apk));
            angaben.put("stand", Files.getLastModifiedTime(apk).toInstant().toString());
        } catch (IOException fehler) {
            LOG.debug("Groesse nicht lesbar", fehler);
        }
        angaben.putAll(angabenLesen(ordner.resolve(name).resolve(ANGABEN)));
        return angaben;
    }

    private Map<String, Object> angabenLesen(Path datei) {
        Map<String, Object> angaben = new HashMap<>();
        if (!Files.isRegularFile(datei)) {
            return angaben;
        }
        try {
            JsonNode inhalt = json.readTree(Files.readAllBytes(datei));
            angaben.put("versionCode", inhalt.path("versionCode").asInt());
            if (inhalt.hasNonNull("versionName")) {
                angaben.put("versionName", inhalt.get("versionName").asText());
            }
            if (inhalt.hasNonNull("hinweis")) {
                angaben.put("hinweis", inhalt.get("hinweis").asText());
            }
        } catch (IOException fehler) {
            LOG.info("app.json nicht lesbar: {}", fehler.toString());
        }
        return angaben;
    }

    public int versionCode(String fach) {
        Object wert = angabenLesen(ordner.resolve(fach).resolve(ANGABEN)).get("versionCode");
        return wert instanceof Integer ? (Integer) wert : 0;
    }

    // ------------------------------------------------------------- Nachsehen

    /**
     * Sieht nach einer neueren App. Mit {@code sofort} fragt der Server jetzt
     * bei GitHub; ohne nur, wenn die gemerkte Liste aelter als eine Woche ist.
     */
    public synchronized Map<String, Object> nachsehen(boolean sofort) {
        if (!holen) {
            letzteMeldung = "Selbst holen ist ausgeschaltet";
            return stand();
        }
        if (token.isEmpty()) {
            letzteMeldung = "Ohne Zugangsschluessel (TAGSTOCK_GITHUB_TOKEN) holt der Server nichts";
            return stand();
        }
        try {
            neuesteHolen(quelle.liste(sofort).liste, sofort);
        } catch (Exception fehler) {
            letzteMeldung = "Nicht erreichbar: " + fehler.getMessage();
            LOG.info("App konnte nicht geholt werden: {}", fehler.toString());
        }
        return stand();
    }

    /**
     * Sucht in der Liste die neueste App und laedt sie, wenn sie neuer ist als
     * die vorhandene. Die Nummer steht schon im Namen der Marke
     * ({@code app-v2.2.0+8}); die Liste allein genuegt also, um zu sehen, ob
     * es etwas zu tun gibt - ohne einen einzigen weiteren Abruf.
     */
    private void neuesteHolen(JsonNode liste, boolean sofort)
            throws IOException, InterruptedException {
        String marke = null;
        String angabenUrl = null;
        String apkUrl = null;
        // Die Liste kommt neueste zuerst; die erste mit beiden Dateien zaehlt.
        for (JsonNode veroeffentlichung : liste) {
            if (veroeffentlichung.path("draft").asBoolean()) {
                continue;
            }
            // Neben den App-Fassungen stehen dort auch die des Servers.
            String name = veroeffentlichung.path("tag_name").asText("");
            if (!name.startsWith("app-v")) {
                continue;
            }
            String einAngaben = null;
            String einApk = null;
            for (JsonNode anhang : veroeffentlichung.path("assets")) {
                String datei = anhang.path("name").asText();
                if (ANGABEN.equals(datei)) {
                    einAngaben = anhang.path("url").asText();
                } else if (APK.equals(datei)) {
                    einApk = anhang.path("url").asText();
                }
            }
            if (einAngaben != null && einApk != null) {
                marke = name;
                angabenUrl = einAngaben;
                apkUrl = einApk;
                break;
            }
        }
        if (marke == null) {
            // Noch nie nachgesehen ist kein Fehler - nur noch nichts da.
            letzteMeldung = liste.size() == 0 ? null
                    : "Keine Veroeffentlichung mit " + APK + " gefunden";
            return;
        }

        int habenWir = versionCode(AKTUELL);
        int laut = nummerAusMarke(marke);
        if (laut > 0 && laut <= habenWir) {
            letzteMeldung = null;
            return;
        }
        if (!sofort && marke.equals(gescheitertBei)) {
            // Schon einmal schiefgegangen; nur auf ausdrueckliches Nachsehen
            // wird es erneut versucht.
            return;
        }

        try {
            String angaben = new String(anfordern(angabenUrl, true, ALS_DATEI),
                    StandardCharsets.UTF_8);
            int neueVersion = json.readTree(angaben).path("versionCode").asInt();
            if (neueVersion == 0) {
                letzteMeldung = "Die Veroeffentlichung nennt keine versionCode";
                gescheitertBei = marke;
                return;
            }
            if (neueVersion <= habenWir) {
                letzteMeldung = null;
                return;
            }

            LOG.info("Neue App gefunden: {} (bisher {})", neueVersion, habenWir);
            byte[] apk = anfordern(apkUrl, true, ALS_DATEI);
            if (apk.length < 1024) {
                letzteMeldung = "Heruntergeladene Datei ist zu klein";
                gescheitertBei = marke;
                return;
            }

            sicherungAnlegen(habenWir);
            umlegen(apk, angaben);
            letzteMeldung = null;
            gescheitertBei = null;
        } catch (IOException fehler) {
            gescheitertBei = marke;
            throw fehler;
        }
    }

    /** Die versionCode aus einer Marke wie {@code app-v2.2.0+8}; 0, wenn keine drinsteht. */
    public static int nummerAusMarke(String marke) {
        int plus = marke.lastIndexOf('+');
        if (plus < 0 || plus == marke.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(marke.substring(plus + 1));
        } catch (NumberFormatException unbrauchbar) {
            return 0;
        }
    }

    /** Die bisherige Fassung wandert nach "vorher", die neue wird "aktuell". */
    private void umlegen(byte[] apk, String angaben) throws IOException {
        Path aktuell = ordner.resolve(AKTUELL);
        Path vorher = ordner.resolve(VORHER);
        Files.createDirectories(aktuell);
        Files.createDirectories(vorher);

        if (Files.isRegularFile(aktuell.resolve(APK))) {
            Files.move(aktuell.resolve(APK), vorher.resolve(APK),
                    StandardCopyOption.REPLACE_EXISTING);
            if (Files.isRegularFile(aktuell.resolve(ANGABEN))) {
                Files.move(aktuell.resolve(ANGABEN), vorher.resolve(ANGABEN),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        // Erst neben die Zieldatei schreiben, dann umbenennen: so gibt es nie
        // eine halb heruntergeladene APK zu sehen.
        Path teil = aktuell.resolve(APK + ".teil");
        Files.write(teil, apk);
        Files.move(teil, aktuell.resolve(APK), StandardCopyOption.REPLACE_EXISTING);
        Files.write(aktuell.resolve(ANGABEN), angaben.getBytes(StandardCharsets.UTF_8));
    }

    /** Sicherung der Datenbank, passend zu der Fassung, die bisher lief. */
    private void sicherungAnlegen(int bisherigeVersion) {
        try {
            Files.createDirectories(sicherungen);
            String zeit = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"));
            Path ziel = sicherungen.resolve(
                    "vor-app-" + (bisherigeVersion == 0 ? "erst" : bisherigeVersion)
                            + "-" + zeit + ".db");
            if (Files.exists(ziel)) {
                return;
            }
            // VACUUM INTO schreibt eine saubere Kopie, auch waehrend der Betrieb laeuft.
            jdbc.execute("VACUUM INTO '" + ziel.toString().replace("'", "''") + "'");
            LOG.info("Sicherung vor dem Wechsel: {}", ziel);
            aeltereSicherungenAufraeumen();
        } catch (Exception fehler) {
            LOG.warn("Sicherung nicht moeglich: {}", fehler.toString());
        }
    }

    /** Die letzten zehn Sicherungen reichen; aeltere raeumt der Server weg. */
    private void aeltereSicherungenAufraeumen() throws IOException {
        List<Path> dateien = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(sicherungen)) {
            stream.filter(p -> p.getFileName().toString().startsWith("vor-app-"))
                    .sorted()
                    .forEach(dateien::add);
        }
        for (int i = 0; i < dateien.size() - 10; i++) {
            Files.deleteIfExists(dateien.get(i));
        }
    }

    /**
     * Anhaenge brauchen ausdruecklich den Rohinhalt. Fragt man sie als JSON,
     * antwortet GitHub mit der Beschreibung des Anhangs - nicht mit der Datei.
     */
    private static final String ALS_DATEI = "application/octet-stream";

    /**
     * Holt eine Adresse. Bei einer Weiterleitung wird der Zugangsschluessel
     * nicht mitgeschickt - der Ablageort weist eine zweite Anmeldung sonst ab.
     */
    private byte[] anfordern(String adresse, boolean mitToken, String annahme)
            throws IOException, InterruptedException {
        HttpRequest.Builder bau = HttpRequest.newBuilder(URI.create(adresse))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "TagStock-Server")
                .header("Accept", annahme)
                .GET();
        if (mitToken && !token.isEmpty()) {
            bau.header("Authorization", "Bearer " + token);
        }
        HttpResponse<byte[]> antwort = client.send(bau.build(),
                HttpResponse.BodyHandlers.ofByteArray());
        int status = antwort.statusCode();
        if (status >= 300 && status < 400) {
            String weiter = antwort.headers().firstValue("location").orElse(null);
            if (weiter == null) {
                throw new IOException("Weiterleitung ohne Ziel");
            }
            return anfordern(weiter, false, annahme);
        }
        if (status >= 400) {
            throw new IOException("Antwort " + status + " von " + adresse);
        }
        return antwort.body();
    }
}
