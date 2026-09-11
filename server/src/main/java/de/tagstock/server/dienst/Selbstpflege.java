package de.tagstock.server.dienst;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der Server bringt sich selbst auf den neuesten Stand - ohne dass jemand auf
 * den Host muss und ohne Zugriff auf Docker.
 *
 * <p>Der Kniff: Die laufende Fassung kann als Jar im Volume liegen. Der Start
 * des Containers nimmt diese, wenn es sie gibt, sonst die aus dem Abbild
 * (siehe start.sh). Aktualisieren heisst damit: neues Jar ins Volume legen und
 * den Dienst beenden - Docker startet den Container neu, und der nimmt die
 * neue Fassung. Startet sie dreimal nicht, legt start.sh sie beiseite.</p>
 *
 * <p>Vor jedem Wechsel wird die Datenbank gesichert, und die bisherige Fassung
 * bleibt als Rueckfallebene liegen.</p>
 */
@Component
public class Selbstpflege implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(Selbstpflege.class);
    private static final String JAR = "tagstock-server.jar";
    private static final String VORHER = "tagstock-server-vorher.jar";
    private static final String ANGABEN = "server.json";

    private final BuildProperties bau;
    private final JdbcTemplate jdbc;
    private final Path ordner;
    private final Path sicherungen;
    private final String quelle;
    private final String token;
    private final boolean erlaubt;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client;

    public Selbstpflege(BuildProperties bau, JdbcTemplate jdbc,
                        @Value("${tagstock.server-ordner:/data/server}") String ordner,
                        @Value("${tagstock.sicherungen-ordner:/data/sicherungen}") String sicherungen,
                        @Value("${tagstock.server-quelle:}") String quelle,
                        @Value("${tagstock.github-token:}") String token,
                        @Value("${tagstock.selbst-aktualisieren:true}") boolean erlaubt) {
        this.bau = bau;
        this.jdbc = jdbc;
        this.ordner = Paths.get(ordner).toAbsolutePath().normalize();
        this.sicherungen = Paths.get(sicherungen).toAbsolutePath().normalize();
        this.quelle = quelle == null || quelle.isBlank()
                ? "https://api.github.com/repos/hunterfast/TagStock/releases?per_page=20"
                : quelle.trim();
        this.token = token == null ? "" : token.trim();
        this.erlaubt = erlaubt;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Der Start hat geklappt - der Zaehler von start.sh darf zurueck auf null.
     * Erst damit gilt eine nachgeladene Fassung als brauchbar.
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent ereignis) {
        try {
            Files.createDirectories(ordner);
            Files.deleteIfExists(ordner.resolve("startversuche"));
        } catch (IOException fehler) {
            LOG.debug("Startzaehler nicht zuruecksetzbar", fehler);
        }
    }

    public boolean moeglich() {
        return erlaubt && !token.isEmpty();
    }

    /** Was liegt an: laufende Fassung, verfuegbare, Rueckfallebene. */
    public Map<String, Object> stand(boolean nachsehen) {
        Map<String, Object> ergebnis = new LinkedHashMap<>();
        ergebnis.put("version", bau.getVersion());
        ergebnis.put("gebautAm", bau.getTime() == null ? null : bau.getTime().toString());
        ergebnis.put("ausVolume", Files.isRegularFile(ordner.resolve(JAR)));
        ergebnis.put("rueckwegMoeglich", Files.isRegularFile(ordner.resolve(VORHER)));
        ergebnis.put("selbstMoeglich", moeglich());
        if (!erlaubt) {
            ergebnis.put("hinweis", "Selbst aktualisieren ist ausgeschaltet");
            return ergebnis;
        }
        if (token.isEmpty()) {
            ergebnis.put("hinweis", "Ohne Zugangsschluessel (TAGSTOCK_GITHUB_TOKEN)"
                    + " kann der Server nichts holen");
            return ergebnis;
        }
        if (!nachsehen) {
            return ergebnis;
        }
        try {
            Veroeffentlichung neueste = neuesteSuchen();
            if (neueste == null) {
                ergebnis.put("hinweis", "Keine Serverfassung veroeffentlicht");
                return ergebnis;
            }
            ergebnis.put("neuesteVersion", neueste.version);
            ergebnis.put("neuerVorhanden", istNeuer(neueste.version, bau.getVersion()));
            if (neueste.hinweis != null && !neueste.hinweis.isEmpty()) {
                ergebnis.put("neuesteHinweis", neueste.hinweis);
            }
        } catch (Exception fehler) {
            ergebnis.put("hinweis", "Nicht erreichbar: " + fehler.getMessage());
        }
        return ergebnis;
    }

    /**
     * Holt die neueste Fassung, sichert die Datenbank und beendet den Dienst.
     * Docker startet den Container neu - dann laeuft die neue Fassung.
     */
    public synchronized Map<String, Object> einspielen() throws IOException, InterruptedException {
        Map<String, Object> ergebnis = new LinkedHashMap<>();
        if (!moeglich()) {
            ergebnis.put("gewechselt", false);
            ergebnis.put("hinweis", erlaubt ? "Kein Zugangsschluessel hinterlegt"
                    : "Selbst aktualisieren ist ausgeschaltet");
            return ergebnis;
        }
        Veroeffentlichung neueste = neuesteSuchen();
        if (neueste == null || !istNeuer(neueste.version, bau.getVersion())) {
            ergebnis.put("gewechselt", false);
            ergebnis.put("hinweis", "Es gibt nichts Neueres");
            return ergebnis;
        }

        byte[] inhalt = anfordern(neueste.jarUrl, true, ALS_DATEI);
        if (inhalt.length < 1024 * 1024 || inhalt[0] != 'P' || inhalt[1] != 'K') {
            throw new IOException("Die heruntergeladene Datei ist kein brauchbares Jar");
        }

        Files.createDirectories(ordner);
        sicherungAnlegen(bau.getVersion());

        Path jetzt = ordner.resolve(JAR);
        if (Files.isRegularFile(jetzt)) {
            Files.move(jetzt, ordner.resolve(VORHER), StandardCopyOption.REPLACE_EXISTING);
        }
        Path teil = ordner.resolve(JAR + ".teil");
        Files.write(teil, inhalt);
        Files.move(teil, jetzt, StandardCopyOption.REPLACE_EXISTING);
        Files.write(ordner.resolve(ANGABEN),
                ("{\"version\":\"" + neueste.version + "\"}").getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(ordner.resolve("startversuche"));

        LOG.info("Fassung {} liegt bereit - der Dienst startet jetzt neu", neueste.version);
        ergebnis.put("gewechselt", true);
        ergebnis.put("neueVersion", neueste.version);
        ergebnis.put("hinweis", "Der Server startet neu und ist gleich wieder da");
        neustart();
        return ergebnis;
    }

    /** Zurueck auf die Fassung davor - ebenfalls ueber einen Neustart. */
    public synchronized Map<String, Object> zurueck() throws IOException {
        Map<String, Object> ergebnis = new LinkedHashMap<>();
        Path vorher = ordner.resolve(VORHER);
        Path jetzt = ordner.resolve(JAR);
        if (!Files.isRegularFile(vorher)) {
            // Ohne eigene Vorgaengerdatei bleibt der Weg zurueck ins Abbild.
            if (!Files.isRegularFile(jetzt)) {
                ergebnis.put("gewechselt", false);
                ergebnis.put("hinweis", "Es läuft bereits die Fassung aus dem Abbild");
                return ergebnis;
            }
            Files.move(jetzt, ordner.resolve(JAR + ".beiseite"),
                    StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(vorher, jetzt, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(ordner.resolve("startversuche"));
        ergebnis.put("gewechselt", true);
        ergebnis.put("hinweis", "Der Server startet neu mit der vorherigen Fassung");
        neustart();
        return ergebnis;
    }

    /**
     * Beendet den Dienst, kurz nachdem die Antwort draussen ist. Der Container
     * laeuft mit "restart unless-stopped" und kommt damit von selbst wieder.
     */
    private void neustart() {
        Thread nachlauf = new Thread(() -> {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException fehler) {
                Thread.currentThread().interrupt();
            }
            LOG.info("Dienst wird fuer den Wechsel beendet");
            Runtime.getRuntime().halt(0);
        }, "neustart");
        nachlauf.setDaemon(false);
        nachlauf.start();
    }

    // ------------------------------------------------------------- Suchen

    /** Angaben zu einer veroeffentlichten Serverfassung. */
    private static final class Veroeffentlichung {
        String version;
        String jarUrl;
        String hinweis;
    }

    private Veroeffentlichung neuesteSuchen() throws IOException, InterruptedException {
        JsonNode liste = json.readTree(anfordern(quelle, true, ALS_JSON));
        for (JsonNode eintrag : liste) {
            if (eintrag.path("draft").asBoolean()) {
                continue;
            }
            String marke = eintrag.path("tag_name").asText("");
            if (!marke.startsWith("server-v")) {
                continue;
            }
            Veroeffentlichung gefunden = new Veroeffentlichung();
            gefunden.version = marke.substring("server-v".length());
            gefunden.hinweis = eintrag.path("body").asText(null);
            for (JsonNode anhang : eintrag.path("assets")) {
                if (JAR.equals(anhang.path("name").asText())) {
                    gefunden.jarUrl = anhang.path("url").asText();
                }
            }
            if (gefunden.jarUrl != null) {
                return gefunden;
            }
        }
        return null;
    }

    /** Vergleicht Fassungen der Form 2.1.0 - hoehere Zahl gewinnt. */
    public static boolean istNeuer(String kandidat, String laufend) {
        if (kandidat == null || kandidat.isEmpty()) {
            return false;
        }
        if (laufend == null || laufend.isEmpty()) {
            return true;
        }
        String[] a = kandidat.split("\\.");
        String[] b = laufend.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int links = i < a.length ? zahl(a[i]) : 0;
            int rechts = i < b.length ? zahl(b[i]) : 0;
            if (links != rechts) {
                return links > rechts;
            }
        }
        return false;
    }

    private static int zahl(String wert) {
        StringBuilder ziffern = new StringBuilder();
        for (int i = 0; i < wert.length() && Character.isDigit(wert.charAt(i)); i++) {
            ziffern.append(wert.charAt(i));
        }
        return ziffern.length() == 0 ? 0 : Integer.parseInt(ziffern.toString());
    }

    private void sicherungAnlegen(String bisher) {
        try {
            Files.createDirectories(sicherungen);
            String zeit = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"));
            Path ziel = sicherungen.resolve("vor-server-" + bisher + "-" + zeit + ".db");
            if (!Files.exists(ziel)) {
                jdbc.execute("VACUUM INTO '" + ziel.toString().replace("'", "''") + "'");
                LOG.info("Sicherung vor dem Wechsel: {}", ziel);
            }
        } catch (Exception fehler) {
            LOG.warn("Sicherung nicht moeglich: {}", fehler.toString());
        }
    }

    /** Die Liste kommt als JSON, der Anhang als Datei - sonst schickt GitHub
     * dessen Beschreibung statt des Inhalts. */
    private static final String ALS_JSON = "application/vnd.github+json";
    private static final String ALS_DATEI = "application/octet-stream";

    private byte[] anfordern(String adresse, boolean mitToken, String annahme)
            throws IOException, InterruptedException {
        HttpRequest.Builder bauen = HttpRequest.newBuilder(URI.create(adresse))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "TagStock-Server")
                .header("Accept", annahme)
                .GET();
        if (mitToken && !token.isEmpty()) {
            bauen.header("Authorization", "Bearer " + token);
        }
        HttpResponse<byte[]> antwort = client.send(bauen.build(),
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
