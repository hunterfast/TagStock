package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * Das erste Konto betreut den Server: es sieht jedes Lager und darf dort
 * arbeiten, auch ohne Mitglied zu sein. Alle anderen sehen nur ihre eigenen.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:build/tmp/verwaltung-test.db",
        "tagstock.app-holen=false",
        "tagstock.update-pruefen=false",
        "tagstock.registrierungs-code="})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VerwaltungTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private static String verwalterToken;
    private static String gastToken;
    private static String fremdesTeam;

    @BeforeAll
    static void datenbankLeeren() {
        File datei = new File("build/tmp/verwaltung-test.db");
        if (datei.exists() && !datei.delete()) {
            throw new IllegalStateException("Testdatenbank nicht loeschbar");
        }
        //noinspection ResultOfMethodCallIgnored
        datei.getParentFile().mkdirs();
    }

    private JsonNode senden(MockHttpServletRequestBuilder anfrage, int erwartet) throws Exception {
        MvcResult ergebnis = mockMvc.perform(anfrage).andReturn();
        String inhalt = new String(ergebnis.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertEquals(erwartet, ergebnis.getResponse().getStatus(), "Antwort: " + inhalt);
        return inhalt.isEmpty() ? mapper.createObjectNode() : mapper.readTree(inhalt);
    }

    private String konto(String email) throws Exception {
        return senden(post("/api/v1/auth/registrieren")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"name\":\"" + email
                        + "\",\"passwort\":\"geheim1234\"}"), 200)
                .get("token").asText();
    }

    private MockHttpServletRequestBuilder mit(MockHttpServletRequestBuilder anfrage, String token) {
        return anfrage.header("Authorization", "Bearer " + token);
    }

    @Test
    @Order(1)
    void dasErsteKontoBetreutDenServer() throws Exception {
        verwalterToken = konto("erste@test.de");
        JsonNode ich = senden(mit(get("/api/v1/ich"), verwalterToken), 200);
        assertTrue(ich.get("benutzer").get("verwalter").asBoolean(), "Antwort: " + ich);

        gastToken = konto("zweite@test.de");
        JsonNode gast = senden(mit(get("/api/v1/ich"), gastToken), 200);
        assertEquals(false, gast.get("benutzer").get("verwalter").asBoolean());
    }

    @Test
    @Order(2)
    void dieBetreuungSiehtAuchFremdeLager() throws Exception {
        // Das zweite Konto legt ein eigenes Lager an.
        fremdesTeam = senden(mit(post("/api/v1/teams"), gastToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Keller von Gast\"}"), 200).get("id").asText();
        senden(mit(post("/api/v1/teams/" + fremdesTeam + "/artikel"), gastToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bohrmaschine\"}"), 200);

        JsonNode alle = senden(mit(get("/api/v1/verwaltung/teams"), verwalterToken), 200);
        assertEquals(1, alle.size(), "Antwort: " + alle);
        assertEquals("Keller von Gast", alle.get(0).get("name").asText());
        assertEquals(1, alle.get(0).get("anzahlArtikel").asInt());
        assertEquals(1, alle.get(0).get("anzahlMitglieder").asInt());

        // Und darf darin arbeiten, ohne Mitglied zu sein.
        JsonNode bestand = senden(mit(get("/api/v1/teams/" + fremdesTeam + "/artikel"),
                verwalterToken), 200);
        assertEquals("Bohrmaschine", bestand.get(0).get("name").asText());
        senden(mit(post("/api/v1/teams/" + fremdesTeam + "/artikel"), verwalterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Von der Betreuung angelegt\"}"), 200);
    }

    @Test
    @Order(3)
    void werNichtBetreutSiehtNurSeineLager() throws Exception {
        senden(mit(get("/api/v1/verwaltung/teams"), gastToken), 403);
        senden(get("/api/v1/verwaltung/teams"), 401);

        // Ein drittes Konto kommt an das fremde Lager nicht heran.
        String fremdToken = konto("dritte@test.de");
        senden(mit(get("/api/v1/teams/" + fremdesTeam + "/artikel"), fremdToken), 403);
    }

    @Test
    @Order(4)
    void dieOberflaecheWirdAusgeliefert() throws Exception {
        MvcResult seite = mockMvc.perform(get("/index.html")).andReturn();
        assertEquals(200, seite.getResponse().getStatus());
        String inhalt = new String(seite.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertTrue(inhalt.contains("oberflaeche.js"), "Startseite ohne Oberflaeche");
    }
}
