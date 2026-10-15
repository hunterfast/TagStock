package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

import de.tagstock.server.dienst.Appwache;
import de.tagstock.server.dienst.Veroeffentlichungen;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Der Server fragt bei GitHub nur, wenn jemand den Stand wissen will - und
 * auch dann hoechstens einmal je Woche. Geprueft an einem kleinen Server, der
 * sich wie GitHub verhaelt und mitzaehlt, wie oft gefragt wurde.
 */
class VeroeffentlichungenTest {

    private static final long TAG_MS = 24L * 60L * 60L * 1000L;
    private final ObjectMapper json = new ObjectMapper();

    private HttpServer github;
    private final AtomicInteger anfragen = new AtomicInteger();
    private final AtomicInteger mitInhalt = new AtomicInteger();
    private final AtomicReference<String> etag = new AtomicReference<>("\"eins\"");
    private Path ordner;
    private String adresse;

    @BeforeEach
    void vorbereiten() throws IOException {
        ordner = Files.createTempDirectory("veroeffentlichungen-test");
        github = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        github.createContext("/releases", austausch -> {
            anfragen.incrementAndGet();
            austausch.getResponseHeaders().add("ETag", etag.get());
            if (etag.get().equals(austausch.getRequestHeaders().getFirst("If-None-Match"))) {
                austausch.sendResponseHeaders(304, -1);
                austausch.close();
                return;
            }
            mitInhalt.incrementAndGet();
            byte[] inhalt = "[{\"tag_name\":\"server-v9.9.9\"}]".getBytes(StandardCharsets.UTF_8);
            austausch.sendResponseHeaders(200, inhalt.length);
            austausch.getResponseBody().write(inhalt);
            austausch.close();
        });
        github.start();
        adresse = "http://127.0.0.1:" + github.getAddress().getPort() + "/releases";
    }

    @AfterEach
    void aufraeumen() throws IOException {
        github.stop(0);
        try (var dateien = Files.walk(ordner)) {
            dateien.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    private Veroeffentlichungen quelle(boolean automatisch) {
        return new Veroeffentlichungen(ordner.toString(), adresse, "token", automatisch, 7);
    }

    @Test
    void vonSelbstFragtNiemand() {
        // Anlegen allein - wie beim Start des Servers - fragt nicht nach.
        quelle(true);
        assertEquals(0, anfragen.get());
    }

    @Test
    void hoechstensEinmalJeWoche() throws Exception {
        Veroeffentlichungen quelle = quelle(true);

        // Die erste Anfrage der App: noch nichts gemerkt, also fragen.
        assertTrue(quelle.liste(false).frisch);
        assertEquals(1, anfragen.get());

        // Jede weitere in derselben Woche kommt aus dem Speicher.
        for (int i = 0; i < 20; i++) {
            assertFalse(quelle.liste(false).frisch);
        }
        assertEquals(1, anfragen.get());
        assertEquals("server-v9.9.9", quelle.liste(false).liste.get(0)
                .path("tag_name").asText());
    }

    @Test
    void aufKnopfdruckWirdSofortGefragt() throws Exception {
        Veroeffentlichungen quelle = quelle(true);
        quelle.liste(false);
        assertTrue(quelle.liste(true).frisch);
        assertEquals(2, anfragen.get());
    }

    @Test
    void unveraendertKostetNurDieKennung() throws Exception {
        Veroeffentlichungen quelle = quelle(true);
        quelle.liste(true);
        quelle.liste(true);
        quelle.liste(true);
        // Dreimal gefragt, aber nur beim ersten Mal kam die Liste mit.
        assertEquals(3, anfragen.get());
        assertEquals(1, mitInhalt.get());
        assertEquals(1, quelle.liste(false).liste.size());

        // Hat sich etwas getan, kommt die neue Liste.
        etag.set("\"zwei\"");
        quelle.liste(true);
        assertEquals(2, mitInhalt.get());
    }

    @Test
    void einNeustartFragtNichtErneut() throws Exception {
        quelle(true).liste(false);
        assertEquals(1, anfragen.get());

        // Wie nach einem Neustart: neue Instanz, gemerkte Datei.
        Veroeffentlichungen nachNeustart = quelle(true);
        assertFalse(nachNeustart.liste(false).frisch);
        assertEquals(1, anfragen.get());
        assertEquals(1, nachNeustart.liste(false).liste.size());
    }

    @Test
    void nachEinerWocheWirdWiederGefragt() throws Exception {
        quelle(true).liste(false);
        // Den gemerkten Zeitpunkt acht Tage zurueckdrehen.
        Path datei = ordner.resolve("veroeffentlichungen.json");
        ObjectNode inhalt = (ObjectNode) json.readTree(Files.readAllBytes(datei));
        inhalt.put("geholtAm", System.currentTimeMillis() - 8 * TAG_MS);
        Files.write(datei, json.writeValueAsBytes(inhalt));

        Veroeffentlichungen spaeter = quelle(true);
        assertTrue(spaeter.liste(false).frisch);
        assertEquals(2, anfragen.get());
        // Unveraendert - also ohne die Liste erneut zu uebertragen.
        assertEquals(1, mitInhalt.get());
    }

    @Test
    void ausgeschaltetFragtNurAufKnopfdruck() throws Exception {
        Veroeffentlichungen quelle = quelle(false);
        assertFalse(quelle.liste(false).frisch);
        assertEquals(0, quelle.liste(false).liste.size());
        assertEquals(0, anfragen.get());

        assertTrue(quelle.liste(true).frisch);
        assertEquals(1, anfragen.get());
    }

    @Test
    void ohneZugangsschluesselGehtNichtsRaus() {
        Veroeffentlichungen ohne = new Veroeffentlichungen(ordner.toString(), adresse, "",
                true, 7);
        assertThrows(IOException.class, () -> ohne.liste(true));
        assertEquals(0, anfragen.get());
    }

    @Test
    void obEsEineNeueAppGibtStehtSchonInDerMarke() {
        // Ohne extra Abruf: die Nummer steht hinter dem Pluszeichen.
        assertEquals(8, Appwache.nummerAusMarke("app-v2.2.0+8"));
        assertEquals(2, Appwache.nummerAusMarke("app-v2.0+2"));
        assertEquals(0, Appwache.nummerAusMarke("app-v2.2.0"));
        assertEquals(0, Appwache.nummerAusMarke("app-v2.2.0+"));
        assertEquals(0, Appwache.nummerAusMarke("app-v2.2.0+x"));
    }
}
