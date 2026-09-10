package de.tagstock.server.dienst;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Merkt, wenn es einen neueren Stand gibt. Verglichen wird der Zeitpunkt, zu
 * dem dieses Abbild gebaut wurde, mit der letzten Aenderung im Zweig auf
 * GitHub. Aktualisiert wird nichts von selbst - der Server sagt nur Bescheid,
 * die Entscheidung bleibt beim Betreiber.
 */
@Component
public class Aktualisierungswache {

    private static final Logger LOG = LoggerFactory.getLogger(Aktualisierungswache.class);
    private static final long NACHSEHEN_ALLE_MS = 24L * 60L * 60L * 1000L;

    private final boolean pruefen;
    private final String quelle;
    private final Instant gebautAm;
    private final String version;
    private final HttpClient client;

    private long zuletztGeprueft;
    private Map<String, Object> letzterStand;

    public Aktualisierungswache(BuildProperties bau,
                                @Value("${tagstock.update-pruefen:true}") boolean pruefen,
                                @Value("${tagstock.update-quelle:}") String quelle) {
        this.pruefen = pruefen;
        this.quelle = quelle == null || quelle.isBlank()
                ? "https://api.github.com/repos/hunterfast/TagStock/commits/"
                + "claude/android-lager-app-barcode-nfc-qe3kev"
                : quelle.trim();
        this.gebautAm = bau.getTime();
        this.version = bau.getVersion();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    }

    public String version() {
        return version;
    }

    public Instant gebautAm() {
        return gebautAm;
    }

    /** Antwort fuer die App; hoechstens einmal am Tag wird wirklich nachgesehen. */
    public synchronized Map<String, Object> stand() {
        Map<String, Object> ergebnis = new HashMap<>();
        ergebnis.put("version", version);
        ergebnis.put("gebautAm", gebautAm == null ? null : gebautAm.toString());
        ergebnis.put("geprueft", pruefen);

        if (!pruefen) {
            ergebnis.put("aktualisierungVerfuegbar", false);
            return ergebnis;
        }
        long jetzt = System.currentTimeMillis();
        if (letzterStand == null || jetzt - zuletztGeprueft > NACHSEHEN_ALLE_MS) {
            letzterStand = nachsehen();
            zuletztGeprueft = jetzt;
        }
        ergebnis.putAll(letzterStand);
        return ergebnis;
    }

    /** Beim naechsten Aufruf wieder wirklich nachsehen. */
    public synchronized void vergessen() {
        letzterStand = null;
    }

    private Map<String, Object> nachsehen() {
        Map<String, Object> stand = new HashMap<>();
        stand.put("aktualisierungVerfuegbar", false);
        if (gebautAm == null) {
            stand.put("hinweis", "Bauzeitpunkt unbekannt");
            return stand;
        }
        try {
            HttpRequest anfrage = HttpRequest.newBuilder(URI.create(quelle))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "TagStock-Server")
                    .GET()
                    .build();
            HttpResponse<String> antwort = client.send(anfrage,
                    HttpResponse.BodyHandlers.ofString());
            if (antwort.statusCode() >= 400) {
                stand.put("hinweis", "Quelle antwortet mit " + antwort.statusCode());
                return stand;
            }
            String datum = Produktsuche.feldAusJson(antwort.body(), "date");
            String kurzbeschreibung = Produktsuche.feldAusJson(antwort.body(), "message");
            if (datum == null) {
                stand.put("hinweis", "Antwort ohne Datum");
                return stand;
            }
            Instant letzteAenderung = Instant.parse(datum);
            stand.put("letzteAenderung", letzteAenderung.toString());
            if (kurzbeschreibung != null && !kurzbeschreibung.isEmpty()) {
                int zeilenende = kurzbeschreibung.indexOf('\n');
                stand.put("beschreibung", zeilenende > 0
                        ? kurzbeschreibung.substring(0, zeilenende) : kurzbeschreibung);
            }
            // Eine Minute Luft: Bauen dauert, der Zeitstempel liegt danach.
            stand.put("aktualisierungVerfuegbar",
                    letzteAenderung.isAfter(gebautAm.plusSeconds(60)));
        } catch (DateTimeParseException | java.io.IOException fehler) {
            stand.put("hinweis", "Nicht erreichbar");
            LOG.info("Aktualisierungspruefung fehlgeschlagen: {}", fehler.toString());
        } catch (InterruptedException fehler) {
            Thread.currentThread().interrupt();
            stand.put("hinweis", "Abgebrochen");
        }
        return stand;
    }
}
