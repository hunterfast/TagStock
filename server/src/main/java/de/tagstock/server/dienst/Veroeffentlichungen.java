package de.tagstock.server.dienst;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * Die Liste der Veroeffentlichungen auf GitHub - die einzige Stelle, an der
 * der Server nachfragt, ob es eine neue App oder eine neue Serverfassung gibt.
 *
 * <p>Der Server fragt <b>nie von selbst</b>: nicht beim Start, nicht im
 * Hintergrund. Nur wenn die App (oder die Oberflaeche) wissen will, was
 * bereitliegt, und auch dann hoechstens einmal je Woche. Wer es eilig hat,
 * drueckt "Nach Aktualisierungen sehen" - das fragt sofort.</p>
 *
 * <p>Die Liste wird samt Zeitpunkt in einer Datei gemerkt. Sonst stuende nach
 * jedem Neustart - und das Selbstaktualisieren startet neu - die naechste
 * Anfrage wieder bei GitHub.</p>
 *
 * <p>Gefragt wird mit der Kennung der letzten Antwort (ETag). Hat sich seit
 * der letzten Woche nichts getan, antwortet GitHub mit 304 und ohne Inhalt:
 * dann gehen nur ein paar hundert Byte ueber die Leitung statt der ganzen
 * Liste.</p>
 */
@Component
public class Veroeffentlichungen {

    private static final Logger LOG = LoggerFactory.getLogger(Veroeffentlichungen.class);
    private static final String DATEI = "veroeffentlichungen.json";
    private static final long TAG_MS = 24L * 60L * 60L * 1000L;

    /** Was die Liste hergibt - und ob dafuer gerade bei GitHub gefragt wurde. */
    public static final class Stand {
        /** Die Veroeffentlichungen, neueste zuerst; leer, wenn nie geholt. */
        public final JsonNode liste;
        /** true, wenn eben bei GitHub nachgefragt wurde. */
        public final boolean frisch;

        Stand(JsonNode liste, boolean frisch) {
            this.liste = liste;
            this.frisch = frisch;
        }
    }

    private final ObjectMapper json = new ObjectMapper();
    private final Path ablage;
    private final String quelle;
    private final String token;
    private final boolean automatisch;
    private final long abstandMs;
    private final HttpClient client;

    private boolean geladen;
    private JsonNode liste;
    private String kennung;
    private long geholtAm;

    public Veroeffentlichungen(
            @Value("${tagstock.app-ordner:/data/app}") String ordner,
            @Value("${tagstock.quelle:${tagstock.app-quelle:${tagstock.server-quelle:}}}")
            String quelle,
            @Value("${tagstock.github-token:}") String token,
            @Value("${tagstock.update-pruefen:true}") boolean automatisch,
            @Value("${tagstock.nachsehen-tage:7}") int abstandTage) {
        this.ablage = Paths.get(ordner).toAbsolutePath().normalize().resolve(DATEI);
        this.quelle = quelle == null || quelle.isBlank()
                ? "https://api.github.com/repos/hunterfast/TagStock/releases?per_page=30"
                : quelle.trim();
        this.token = token == null ? "" : token.trim();
        // Null Tage hiesse: bei jeder Anfrage - genau das soll nicht sein.
        this.automatisch = automatisch && abstandTage > 0;
        this.abstandMs = Math.max(1, abstandTage) * TAG_MS;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Fragt der Server von sich aus nach, wenn jemand den Stand wissen will? */
    public boolean automatisch() {
        return automatisch;
    }

    /** Abstand zwischen zwei Nachfragen in Tagen. */
    public long abstandTage() {
        return abstandMs / TAG_MS;
    }

    /** Wann zuletzt bei GitHub gefragt wurde - 0, wenn noch nie. */
    public synchronized long geholtAm() {
        laden();
        return geholtAm;
    }

    /**
     * Die Liste. Bei GitHub gefragt wird nur, wenn {@code erzwingen} gesetzt
     * ist (jemand hat ausdruecklich nachsehen lassen) oder wenn die gemerkte
     * Liste aelter als eine Woche ist und das Nachsehen eingeschaltet ist.
     * Sonst kommt sie aus dem Speicher - ohne einen Byte Netzverkehr.
     */
    public synchronized Stand liste(boolean erzwingen) throws IOException, InterruptedException {
        laden();
        long jetzt = System.currentTimeMillis();
        boolean faellig = liste == null || jetzt - geholtAm >= abstandMs;
        if (!erzwingen && !(automatisch && faellig)) {
            return new Stand(liste == null ? json.createArrayNode() : liste, false);
        }
        if (token.isEmpty()) {
            throw new IOException("Ohne Zugangsschluessel (TAGSTOCK_GITHUB_TOKEN) fragt der"
                    + " Server nicht nach");
        }
        holen();
        geholtAm = System.currentTimeMillis();
        speichern();
        return new Stand(liste, true);
    }

    private void holen() throws IOException, InterruptedException {
        HttpRequest.Builder bau = HttpRequest.newBuilder(URI.create(quelle))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "TagStock-Server")
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .GET();
        if (kennung != null && liste != null) {
            bau.header("If-None-Match", kennung);
        }
        HttpResponse<byte[]> antwort = client.send(bau.build(),
                HttpResponse.BodyHandlers.ofByteArray());
        int status = antwort.statusCode();
        if (status == 304) {
            LOG.info("Veroeffentlichungen unveraendert");
            return;
        }
        if (status >= 400) {
            throw new IOException("Antwort " + status + " von " + quelle);
        }
        JsonNode neu = json.readTree(antwort.body());
        if (neu == null || !neu.isArray()) {
            throw new IOException("Die Quelle lieferte keine Liste");
        }
        liste = neu;
        kennung = antwort.headers().firstValue("etag").orElse(null);
        LOG.info("Veroeffentlichungen geholt: {} Eintraege", neu.size());
    }

    /** Einmal aus der Datei lesen - danach haelt der Speicher den Stand. */
    private void laden() {
        if (geladen) {
            return;
        }
        geladen = true;
        if (!Files.isRegularFile(ablage)) {
            return;
        }
        try {
            JsonNode inhalt = json.readTree(Files.readAllBytes(ablage));
            JsonNode gemerkt = inhalt.path("liste");
            if (gemerkt.isArray()) {
                liste = gemerkt;
                kennung = inhalt.hasNonNull("kennung") ? inhalt.get("kennung").asText() : null;
                geholtAm = inhalt.path("geholtAm").asLong(0);
            }
        } catch (IOException fehler) {
            // Eine kaputte Datei kostet nur eine Nachfrage mehr.
            LOG.info("Gemerkte Veroeffentlichungen nicht lesbar: {}", fehler.toString());
        }
    }

    private void speichern() {
        try {
            Files.createDirectories(ablage.getParent());
            ObjectNode inhalt = json.createObjectNode();
            inhalt.put("geholtAm", geholtAm);
            inhalt.put("kennung", kennung);
            inhalt.set("liste", liste);
            Path teil = ablage.resolveSibling(DATEI + ".teil");
            Files.write(teil, json.writeValueAsString(inhalt).getBytes(StandardCharsets.UTF_8));
            Files.move(teil, ablage, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException fehler) {
            LOG.info("Veroeffentlichungen nicht speicherbar: {}", fehler.toString());
        }
    }
}
