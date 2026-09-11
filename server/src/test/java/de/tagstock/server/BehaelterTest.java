package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Behaelter sind Lager im Lager: eine Box, eine Schublade, ein Regal. Wer
 * drin liegt, merkt sich die Kennung des Behaelters - die klebt am Moebel.
 * Geprueft wird, dass diese Verbindung haelt, wenn sich etwas aendert, und
 * dass sich niemand im Kreis einraeumen kann.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:build/tmp/behaelter-test.db",
        "tagstock.bilder-ordner=build/tmp/behaelter-test-bilder",
        "tagstock.app-ordner=build/tmp/behaelter-test-app",
        "tagstock.app-holen=false",
        "tagstock.quelle-beobachten=false",
        "tagstock.update-pruefen=false",
        "tagstock.registrierungs-code="})
@AutoConfigureMockMvc
class BehaelterTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void datenbankLeeren() {
        File datei = new File("build/tmp/behaelter-test.db");
        if (datei.exists() && !datei.delete()) {
            throw new IllegalStateException("Testdatenbank nicht loeschbar");
        }
        //noinspection ResultOfMethodCallIgnored
        datei.getParentFile().mkdirs();
    }

    @Test
    void einraeumenUmziehenUndAufloesen() throws Exception {
        String token = konto("box@test.de");
        String team = team(token, "Werkstatt");

        // Eine Box mit eigener Kennung, und eine Schublade darin.
        String box = anlegen(token, team, "{\"name\":\"Ikea-Box blau\",\"rfidUid\":\"BOX-1\","
                + "\"istBehaelter\":true,\"behaelterArt\":\"Box\"}").get("id").asText();
        JsonNode schublade = anlegen(token, team, "{\"name\":\"Schublade oben\","
                + "\"rfidUid\":\"SCHUB-1\",\"istBehaelter\":true,"
                + "\"behaelterArt\":\"Schublade\",\"behaelterKennung\":\"BOX-1\"}");
        assertEquals("BOX-1", schublade.get("behaelterKennung").asText());
        assertTrue(schublade.get("istBehaelter").asBoolean());

        // Ein Gegenstand in die Schublade.
        String zange = anlegen(token, team, "{\"name\":\"Zange\",\"rfidUid\":\"WZ-1\","
                + "\"behaelterKennung\":\"SCHUB-1\"}").get("id").asText();

        // Ohne eigene Kennung ist niemand ein Behaelter.
        JsonNode fehler = senden(mitToken(post("/api/v1/teams/" + team + "/artikel"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Kiste ohne Schild\",\"istBehaelter\":true}"), 400);
        assertTrue(fehler.get("fehler").asText().contains("eigene Kennung"));

        // In etwas, das kein Behaelter ist, laesst sich nichts legen.
        fehler = senden(mitToken(post("/api/v1/teams/" + team + "/artikel"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Schraube\",\"behaelterKennung\":\"WZ-1\"}"), 400);
        assertTrue(fehler.get("fehler").asText().contains("kein Behälter"));

        // Und auch nicht in etwas, das es nicht gibt.
        senden(mitToken(post("/api/v1/teams/" + team + "/artikel"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nagel\",\"behaelterKennung\":\"GIBTSNICHT\"}"), 400);

        // Die Box in ihre eigene Schublade zu legen waere ein Ring.
        fehler = senden(mitToken(put("/api/v1/teams/" + team + "/artikel/" + box), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ikea-Box blau\",\"rfidUid\":\"BOX-1\","
                        + "\"istBehaelter\":true,\"behaelterKennung\":\"SCHUB-1\"}"), 400);
        assertTrue(fehler.get("fehler").asText().contains("Ring"));

        // Neue Kennung an der Schublade: Der Inhalt zieht mit.
        senden(mitToken(put("/api/v1/teams/" + team + "/artikel/"
                + schublade.get("id").asText()), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Schublade oben\",\"rfidUid\":\"SCHUB-NEU\","
                        + "\"istBehaelter\":true,\"behaelterKennung\":\"BOX-1\"}"), 200);
        assertEquals("SCHUB-NEU", artikel(token, team, zange).get("behaelterKennung").asText());

        // Verschwindet die Schublade, wird ihr Inhalt frei - nicht unauffindbar.
        JsonNode geloescht = senden(mitToken(delete("/api/v1/teams/" + team + "/artikel/"
                + schublade.get("id").asText()), token), 200);
        assertEquals(1, geloescht.get("freigegeben").asInt());
        assertTrue(artikel(token, team, zange).get("behaelterKennung").isNull());
    }

    @Test
    void werKeinBehaelterMehrIstGibtSeinenInhaltFrei() throws Exception {
        String token = konto("kiste@test.de");
        String team = team(token, "Keller");
        String kiste = anlegen(token, team, "{\"name\":\"Kiste\",\"rfidUid\":\"K-1\","
                + "\"istBehaelter\":true}").get("id").asText();
        String buch = anlegen(token, team, "{\"name\":\"Buch\",\"behaelterKennung\":\"K-1\"}")
                .get("id").asText();

        senden(mitToken(put("/api/v1/teams/" + team + "/artikel/" + kiste), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Kiste\",\"rfidUid\":\"K-1\",\"istBehaelter\":false}"), 200);

        assertTrue(artikel(token, team, buch).get("behaelterKennung").isNull());
    }

    // ----------------------------------------------------------- Hilfsmittel

    private JsonNode senden(MockHttpServletRequestBuilder anfrage, int erwarteterStatus)
            throws Exception {
        MvcResult ergebnis = mockMvc.perform(anfrage).andReturn();
        String inhalt = new String(ergebnis.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertEquals(erwarteterStatus, ergebnis.getResponse().getStatus(), "Antwort: " + inhalt);
        return inhalt.isEmpty() ? mapper.createObjectNode() : mapper.readTree(inhalt);
    }

    private MockHttpServletRequestBuilder mitToken(MockHttpServletRequestBuilder anfrage,
                                                   String token) {
        return anfrage.header("Authorization", "Bearer " + token);
    }

    private String konto(String email) throws Exception {
        return senden(post("/api/v1/auth/registrieren")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"name\":\"Test\","
                        + "\"passwort\":\"geheim1234\"}"), 200).get("token").asText();
    }

    private String team(String token, String name) throws Exception {
        return senden(mitToken(post("/api/v1/teams"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"), 200).get("id").asText();
    }

    private JsonNode anlegen(String token, String teamId, String koerper) throws Exception {
        return senden(mitToken(post("/api/v1/teams/" + teamId + "/artikel"), token)
                .contentType(MediaType.APPLICATION_JSON).content(koerper), 200);
    }

    private JsonNode artikel(String token, String teamId, String artikelId) throws Exception {
        JsonNode liste = senden(mitToken(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/teams/" + teamId + "/artikel"), token), 200);
        for (JsonNode eintrag : liste) {
            if (artikelId.equals(eintrag.get("id").asText())) {
                return eintrag;
            }
        }
        assertNull(artikelId, "Artikel nicht in der Liste");
        return null;
    }
}
