package de.tagstock.server.dienst;

import de.tagstock.server.daten.GtinDaten;
import de.tagstock.server.modell.Produktinfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Schlaegt Produktdaten zu einer GTIN nach - aber nur einmal je Nummer. Was
 * einmal geholt wurde, steht im Zwischenspeicher und wird von dort bedient;
 * auch ein Fehlschlag wird gemerkt, damit dieselbe Nummer nicht bei jedem Scan
 * wieder nach draussen geht.
 *
 * <p>Ohne konfigurierten Dienst passiert gar nichts - dann bleibt der Server
 * stumm nach aussen.</p>
 */
@Component
public class Produktsuche {

    private static final Logger LOG = LoggerFactory.getLogger(Produktsuche.class);

    /** Kein Dienst eingestellt: es wird nichts nachgeschlagen. */
    public static final String AUS = "aus";
    public static final String OPENGTINDB = "opengtindb";
    public static final String EANSEARCH = "eansearch";

    private final GtinDaten daten;
    private final String dienst;
    private final String schluessel;
    private final long fehlschlagGueltigMs;
    private final HttpClient client;

    public Produktsuche(GtinDaten daten,
                        @Value("${tagstock.gtin-dienst:aus}") String dienst,
                        @Value("${tagstock.gtin-schluessel:}") String schluessel,
                        @Value("${tagstock.gtin-fehlschlag-tage:7}") int fehlschlagTage) {
        this.daten = daten;
        this.dienst = dienst == null ? AUS : dienst.trim().toLowerCase(Locale.ROOT);
        this.schluessel = schluessel == null ? "" : schluessel.trim();
        this.fehlschlagGueltigMs = (long) fehlschlagTage * 24L * 60L * 60L * 1000L;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean eingeschaltet() {
        return !AUS.equals(dienst);
    }

    public String dienst() {
        return dienst;
    }

    /** Aus dem Zwischenspeicher, sonst beim eingestellten Dienst. */
    public Produktinfo suchen(String gtin) {
        Produktinfo gemerkt = daten.nachGtin(gtin);
        if (gemerkt != null && (gemerkt.gefunden
                || System.currentTimeMillis() - gemerkt.geholtAm < fehlschlagGueltigMs)) {
            gemerkt.ausCache = true;
            return gemerkt;
        }

        Produktinfo frisch = new Produktinfo();
        frisch.gtin = gtin;
        frisch.geholtAm = System.currentTimeMillis();
        frisch.quelle = dienst;
        if (eingeschaltet()) {
            try {
                if (OPENGTINDB.equals(dienst)) {
                    ausOpenGtinDb(gtin, frisch);
                } else if (EANSEARCH.equals(dienst)) {
                    ausEanSearch(gtin, frisch);
                }
            } catch (Exception fehler) {
                LOG.info("Nachschlagen von {} fehlgeschlagen: {}", gtin, fehler.toString());
            }
        }
        daten.merken(frisch);
        return frisch;
    }

    // ------------------------------------------------------------- Anbieter

    /** opengtindb.org antwortet als Text mit Zeilen der Form schluessel=wert. */
    private void ausOpenGtinDb(String gtin, Produktinfo ziel) throws Exception {
        String id = schluessel.isEmpty() ? "400000000" : schluessel;
        String antwort = holen("https://opengtindb.org/?ean=" + gtin
                + "&cmd=query&queryid=" + id);
        String name = null;
        String detail = null;
        String marke = null;
        for (String zeile : antwort.split("\\r?\\n")) {
            int gleich = zeile.indexOf('=');
            if (gleich <= 0) {
                continue;
            }
            String feld = zeile.substring(0, gleich).trim();
            String wert = zeile.substring(gleich + 1).trim();
            if (wert.isEmpty()) {
                continue;
            }
            switch (feld) {
                case "error":
                    if (!"0".equals(wert)) {
                        return;
                    }
                    break;
                case "name":
                    name = wert;
                    break;
                case "detailname":
                    detail = wert;
                    break;
                case "vendor":
                    marke = wert;
                    break;
                default:
                    break;
            }
        }
        if (name == null && detail == null) {
            return;
        }
        ziel.gefunden = true;
        ziel.name = detail == null ? name : (name == null ? detail : name + " " + detail);
        ziel.marke = marke;
    }

    /** ean-search.org liefert JSON; der erste Treffer zaehlt. */
    private void ausEanSearch(String gtin, Produktinfo ziel) throws Exception {
        if (schluessel.isEmpty()) {
            return;
        }
        String antwort = holen("https://api.ean-search.org/api?token=" + schluessel
                + "&op=barcode-lookup&format=json&ean=" + gtin);
        String name = feldAusJson(antwort, "name");
        if (name == null || name.isEmpty()) {
            return;
        }
        ziel.gefunden = true;
        ziel.name = name;
        ziel.marke = feldAusJson(antwort, "issuingCountry");
    }

    private String holen(String adresse) throws Exception {
        HttpRequest anfrage = HttpRequest.newBuilder(URI.create(adresse))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "TagStock-Server")
                .GET()
                .build();
        HttpResponse<byte[]> antwort = client.send(anfrage, HttpResponse.BodyHandlers.ofByteArray());
        if (antwort.statusCode() >= 400) {
            throw new IllegalStateException("Dienst antwortet mit " + antwort.statusCode());
        }
        return new String(antwort.body(), StandardCharsets.UTF_8);
    }

    /** Sehr einfacher Griff in eine flache JSON-Antwort - eine Bibliothek waere hier zu viel. */
    public static String feldAusJson(String json, String feld) {
        String suche = "\"" + feld + "\"";
        int start = json.indexOf(suche);
        if (start < 0) {
            return null;
        }
        int doppelpunkt = json.indexOf(':', start + suche.length());
        if (doppelpunkt < 0) {
            return null;
        }
        int auf = json.indexOf('"', doppelpunkt);
        if (auf < 0) {
            return null;
        }
        StringBuilder wert = new StringBuilder();
        for (int i = auf + 1; i < json.length(); i++) {
            char zeichen = json.charAt(i);
            if (zeichen == '\\' && i + 1 < json.length()) {
                wert.append(json.charAt(++i));
                continue;
            }
            if (zeichen == '"') {
                break;
            }
            wert.append(zeichen);
        }
        return wert.toString();
    }
}
