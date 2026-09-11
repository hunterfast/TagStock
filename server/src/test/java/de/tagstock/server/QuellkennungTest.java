package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import de.tagstock.server.dienst.Quellkennung;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Die Quellwache soll nur dann Arbeit ausloesen, wenn sich wirklich etwas
 * geaendert hat. Geprueft an einem kleinen Server, der sich genauso verhaelt
 * wie GitHub: Kennung mitgeben, bei gleicher Kennung mit 304 antworten.
 */
class QuellkennungTest {

    @Test
    void ersteRundeMerktNurUndMeldetNichts() throws Exception {
        AtomicReference<String> etag = new AtomicReference<>("\"eins\"");
        AtomicInteger anfragen = new AtomicInteger();
        HttpServer server = starten(etag, anfragen);
        try {
            Quellkennung kennung = new Quellkennung("application/json");
            String adresse = "http://127.0.0.1:" + server.getAddress().getPort() + "/releases";

            // Beim Start ist noch nichts bekannt: merken, aber keinen Alarm.
            assertFalse(kennung.veraendert(adresse, ""));
            assertEquals("\"eins\"", kennung.kennung());

            // Unveraendert - der Server antwortet mit 304.
            assertFalse(kennung.veraendert(adresse, ""));

            // Jetzt hat sich etwas getan.
            etag.set("\"zwei\"");
            assertTrue(kennung.veraendert(adresse, ""));
            assertEquals("\"zwei\"", kennung.kennung());

            // Und danach ist wieder Ruhe.
            assertFalse(kennung.veraendert(adresse, ""));
            assertEquals(4, anfragen.get());
        } finally {
            server.stop(0);
        }
    }

    /** Antwortet wie GitHub: mit Kennung, und mit 304, wenn sie passt. */
    private static HttpServer starten(AtomicReference<String> etag, AtomicInteger anfragen)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/releases", austausch -> {
            anfragen.incrementAndGet();
            String mitgebracht = austausch.getRequestHeaders().getFirst("If-None-Match");
            austausch.getResponseHeaders().add("ETag", etag.get());
            if (etag.get().equals(mitgebracht)) {
                austausch.sendResponseHeaders(304, -1);
                austausch.close();
                return;
            }
            byte[] inhalt = "[]".getBytes(StandardCharsets.UTF_8);
            austausch.sendResponseHeaders(200, inhalt.length);
            austausch.getResponseBody().write(inhalt);
            austausch.close();
        });
        server.start();
        return server;
    }
}
