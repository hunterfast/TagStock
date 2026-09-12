package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * Mengen und Verpackungseinheiten ueber die Schnittstelle: Shelly 1 Gen4 im
 * 4er-Pack und ein Patchkabel ohne Packung.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:build/tmp/menge-test.db",
        "tagstock.bilder-ordner=build/tmp/menge-test-bilder",
        "tagstock.app-ordner=build/tmp/menge-test-app",
        "tagstock.app-holen=false",
        "tagstock.quelle-beobachten=false",
        "tagstock.update-pruefen=false",
        "tagstock.registrierungs-code="})
@AutoConfigureMockMvc
class MengeTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void datenbankLeeren() {
        File datei = new File("build/tmp/menge-test.db");
        if (datei.exists() && !datei.delete()) {
            throw new IllegalStateException("Testdatenbank nicht loeschbar");
        }
        //noinspection ResultOfMethodCallIgnored
        datei.getParentFile().mkdirs();
    }

    @Test
    void packungenWerdenNacheinanderAngebrochen() throws Exception {
        String token = konto("menge@test.de");
        String team = team(token, "Werkstatt");

        JsonNode shelly = anlegen(token, team, "{\"name\":\"Shelly 1 Gen4\",\"menge\":5,"
                + "\"istVerpackung\":true,\"packungsGroesse\":4,\"angebrochen\":\"2,3\"}");
        String id = shelly.get("id").asText();
        assertEquals(5, shelly.get("menge").asInt());
        assertEquals("2,3", shelly.get("angebrochen").asText());

        // 2 entnehmen leert die erste offene Packung.
        JsonNode nach = menge(token, team, id, "entnehmen", 2);
        assertEquals(5, nach.get("menge").asInt());
        assertEquals("3", nach.get("angebrochen").asText());

        // Weitere 3 leeren die zweite; danach wird eine volle angebrochen.
        nach = menge(token, team, id, "entnehmen", 4);
        assertEquals(4, nach.get("menge").asInt());
        assertEquals("3", nach.get("angebrochen").asText());

        // Zugang zaehlt Packungen, nicht Stueck.
        nach = menge(token, team, id, "zugang", 2);
        assertEquals(6, nach.get("menge").asInt());

        // Von Hand anbrechen kostet eine volle Packung.
        nach = menge(token, team, id, "anbrechen", 1);
        assertEquals(5, nach.get("menge").asInt());
        assertEquals("3,4", nach.get("angebrochen").asText());
    }

    @Test
    void leererBestandSetztDenStatusUndZugangHoltIhnZurueck() throws Exception {
        String token = konto("leer@test.de");
        String team = team(token, "Keller");
        String id = anlegen(token, team,
                "{\"name\":\"Patchkabel 2 m\",\"menge\":2}").get("id").asText();

        JsonNode nach = menge(token, team, id, "entnehmen", 2);
        assertEquals(0, nach.get("menge").asInt());
        assertEquals("nicht vorhanden", nach.get("status").asText());

        nach = menge(token, team, id, "zugang", 3);
        assertEquals(3, nach.get("menge").asInt());
        assertEquals("vorhanden", nach.get("status").asText());
    }

    @Test
    void mehrAlsVorhandenGehtNichtUndUnsinnWirdGeradegezogen() throws Exception {
        String token = konto("unsinn@test.de");
        String team = team(token, "Lager");
        String id = anlegen(token, team, "{\"name\":\"Nagel\",\"menge\":1}").get("id").asText();
        menge(token, team, id, "entnehmen", 1);

        JsonNode fehler = senden(mitToken(post("/api/v1/teams/" + team + "/artikel/"
                + id + "/menge"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"was\":\"entnehmen\",\"anzahl\":1}"), 400);
        assertTrue(fehler.get("fehler").asText().contains("Nichts mehr"));

        // Ein Rest groesser als die Packung kann es nicht geben.
        JsonNode krumm = senden(mitToken(put("/api/v1/teams/" + team + "/artikel/" + id), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nagel\",\"menge\":2,\"istVerpackung\":true,"
                        + "\"packungsGroesse\":3,\"angebrochen\":\"9,1\"}"), 200);
        assertEquals("3,1", krumm.get("angebrochen").asText());

        // Ohne Verpackungseinheit bleibt von den Packungsangaben nichts uebrig.
        JsonNode glatt = senden(mitToken(put("/api/v1/teams/" + team + "/artikel/" + id), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nagel\",\"menge\":2,\"istVerpackung\":false,"
                        + "\"packungsGroesse\":3,\"angebrochen\":\"2\"}"), 200);
        assertEquals(0, glatt.get("packungsGroesse").asInt());
        assertTrue(glatt.get("angebrochen").isNull());
    }

    // ----------------------------------------------------------- Hilfsmittel

    private JsonNode menge(String token, String team, String id, String was, int anzahl)
            throws Exception {
        return senden(mitToken(post("/api/v1/teams/" + team + "/artikel/" + id + "/menge"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"was\":\"" + was + "\",\"anzahl\":" + anzahl + "}"), 200);
    }

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
}
