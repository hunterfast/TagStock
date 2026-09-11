package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tagstock.server.dienst.Selbstpflege;

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
import java.nio.file.Files;

/**
 * Der Server bringt sich selbst auf den Stand - hier ohne Netz: geprueft wird
 * der Versionsvergleich, wer die Knoepfe druecken darf und dass der
 * Startzaehler nach einem geglueckten Start wieder bei null steht.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:build/tmp/selbst-test.db",
        "tagstock.server-ordner=build/tmp/selbst-test-server",
        "tagstock.app-holen=false",
        "tagstock.update-pruefen=false",
        "tagstock.registrierungs-code="})
@AutoConfigureMockMvc
class SelbstpflegeTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void aufraeumen() throws Exception {
        File datei = new File("build/tmp/selbst-test.db");
        if (datei.exists() && !datei.delete()) {
            throw new IllegalStateException("Testdatenbank nicht loeschbar");
        }
        //noinspection ResultOfMethodCallIgnored
        datei.getParentFile().mkdirs();
        File ordner = new File("build/tmp/selbst-test-server");
        //noinspection ResultOfMethodCallIgnored
        ordner.mkdirs();
        // So sieht es aus, wenn ein Start gerade laeuft.
        Files.write(new File(ordner, "startversuche").toPath(),
                "1".getBytes(StandardCharsets.UTF_8));
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
                .content("{\"email\":\"" + email + "\",\"name\":\"Test\","
                        + "\"passwort\":\"geheim1234\"}"), 200).get("token").asText();
    }

    @Test
    void hoehereFassungenGeltenAlsNeuer() {
        assertTrue(Selbstpflege.istNeuer("2.2.0", "2.1.0"));
        assertTrue(Selbstpflege.istNeuer("2.1.1", "2.1.0"));
        assertTrue(Selbstpflege.istNeuer("3.0.0", "2.9.9"));
        assertFalse(Selbstpflege.istNeuer("2.1.0", "2.1.0"));
        assertFalse(Selbstpflege.istNeuer("2.0.9", "2.1.0"));
        assertFalse(Selbstpflege.istNeuer(null, "2.1.0"));
    }

    @Test
    void einGegluecketerStartSetztDenZaehlerZurueck() {
        // Der Zaehler kommt aus start.sh; laeuft der Dienst, ist er hinfaellig.
        assertFalse(new File("build/tmp/selbst-test-server/startversuche").exists(),
                "Der Startzaehler haette geloescht werden muessen");
    }

    @Test
    void nurDieBetreuungDarfWechseln() throws Exception {
        String verwalter = konto("betreuung@test.de");
        String gast = konto("gast@test.de");

        senden(post("/api/v1/aktualisierung/einspielen"), 401);
        senden(post("/api/v1/aktualisierung/einspielen")
                .header("Authorization", "Bearer " + gast), 403);

        // Ohne Zugangsschluessel holt der Server nichts - sagt das aber sauber.
        JsonNode antwort = senden(post("/api/v1/aktualisierung/einspielen")
                .header("Authorization", "Bearer " + verwalter), 200);
        assertFalse(antwort.get("gewechselt").asBoolean());

        JsonNode stand = senden(get("/api/v1/aktualisierung")
                .header("Authorization", "Bearer " + verwalter), 200);
        assertFalse(stand.get("selbstMoeglich").asBoolean());
        assertFalse(stand.get("ausVolume").asBoolean());
    }
}
