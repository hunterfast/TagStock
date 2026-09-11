package de.tagstock.server.dienst;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
 */
@Component
public class Appwache implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(Appwache.class);
    private static final String APK = "tagstock.apk";
    private static final String ANGABEN = "app.json";
    public static final String AKTUELL = "aktuell";
    public static final String VORHER = "vorher";
    private static final long NACHSEHEN_ALLE_MS = 6L * 60L * 60L * 1000L;

    private final Path ordner;
    private final Path sicherungen;
    private final boolean holen;
    private final String quelle;
    private final String token;
    private final JdbcTemplate jdbc;
    private final HttpClient client;

    private final ObjectMapper json = new ObjectMapper();
    private long zuletztGesehen;
    private String letzteMeldung;

    public Appwache(JdbcTemplate jdbc,
                    @Value("${tagstock.app-ordner:/data/app}") String ordner,
                    @Value("${tagstock.sicherungen-ordner:/data/sicherungen}") String sicherungen,
                    @Value("${tagstock.app-holen:true}") boolean holen,
                    @Value("${tagstock.app-quelle:}") String quelle,
                    @Value("${tagstock.github-token:}") String token) {
        this.jdbc = jdbc;
        this.ordner = Paths.get(ordner).toAbsolutePath().normalize();
        this.sicherungen = Paths.get(sicherungen).toAbsolutePath().normalize();
        this.holen = holen;
        this.quelle = quelle == null || quelle.isBlank()
                ? "https://api.github.com/repos/hunterfast/TagStock/releases?per_page=30"
                : quelle.trim();
        this.token = token == null ? "" : token.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Beim Start einmal nachsehen - im Hintergrund, damit nichts wartet. */
    @Override
    public void run(ApplicationArguments argumente) {
        Thread nachsehen = new Thread(() -> nachsehen(false), "app-wache");
        nachsehen.setDaemon(true);
        nachsehen.start();
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

    /** Sieht nach einer neueren App - hoechstens alle sechs Stunden von selbst. */
    public synchronized Map<String, Object> nachsehen(boolean sofort) {
        long jetzt = System.currentTimeMillis();
        if (!sofort && jetzt - zuletztGesehen < NACHSEHEN_ALLE_MS) {
            return stand();
        }
        zuletztGesehen = jetzt;
        if (!holen) {
            letzteMeldung = "Selbst holen ist ausgeschaltet";
            return stand();
        }
        if (token.isEmpty()) {
            letzteMeldung = "Ohne Zugangsschluessel (TAGSTOCK_GITHUB_TOKEN) holt der Server nichts";
            return stand();
        }
        try {
            neuesteHolen();
        } catch (Exception fehler) {
            letzteMeldung = "Nicht erreichbar: " + fehler.getMessage();
            LOG.info("App konnte nicht geholt werden: {}", fehler.toString());
        }
        return stand();
    }

    private void neuesteHolen() throws IOException, InterruptedException {
        JsonNode liste = json.readTree(anfordern(quelle, true, ALS_JSON));
        String angabenUrl = null;
        String apkUrl = null;
        // Die Liste kommt neueste zuerst; die erste mit beiden Dateien zaehlt.
        for (JsonNode veroeffentlichung : liste) {
            if (veroeffentlichung.path("draft").asBoolean()) {
                continue;
            }
            // Neben den App-Fassungen stehen dort auch die des Servers.
            if (!veroeffentlichung.path("tag_name").asText("").startsWith("app-v")) {
                continue;
            }
            String einAngaben = null;
            String einApk = null;
            for (JsonNode anhang : veroeffentlichung.path("assets")) {
                String name = anhang.path("name").asText();
                if (ANGABEN.equals(name)) {
                    einAngaben = anhang.path("url").asText();
                } else if (APK.equals(name)) {
                    einApk = anhang.path("url").asText();
                }
            }
            if (einAngaben != null && einApk != null) {
                angabenUrl = einAngaben;
                apkUrl = einApk;
                break;
            }
        }
        if (angabenUrl == null) {
            letzteMeldung = "Keine Veroeffentlichung mit " + APK + " gefunden";
            return;
        }

        String angaben = new String(anfordern(angabenUrl, true, ALS_DATEI),
                StandardCharsets.UTF_8);
        int neueVersion = json.readTree(angaben).path("versionCode").asInt();
        int habenWir = versionCode(AKTUELL);
        if (neueVersion == 0) {
            letzteMeldung = "Die Veroeffentlichung nennt keine versionCode";
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
            return;
        }

        sicherungAnlegen(habenWir);
        umlegen(apk, angaben);
        letzteMeldung = null;
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

    /** Die Liste der Veroeffentlichungen kommt als JSON. */
    private static final String ALS_JSON = "application/vnd.github+json";
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
