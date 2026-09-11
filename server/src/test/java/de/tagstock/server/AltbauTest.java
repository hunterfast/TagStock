package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/**
 * Der Server muss auch mit einer Datenbank starten, die aus einer aelteren
 * Fassung stammt. Alle anderen Tests legen eine frische an - und uebersehen
 * damit genau die Fehler, die nur beim Aktualisieren auftreten.
 *
 * <p>Anlass war ein echter Ausfall: Ein Index auf einer neuen Spalte stand in
 * schema.sql. Die laeuft beim Aufbau der Datenquelle, also bevor die
 * Schemapflege die Spalte ergaenzen kann - auf einer bestehenden Datenbank
 * scheiterte das CREATE INDEX, und der Server startete nicht mehr. Auf einer
 * frischen fiel es nicht auf.</p>
 *
 * <p>Dieser Test baut deshalb eine Datenbank im Zustand vor den Behaeltern und
 * laesst den Server darauf los. Startet er nicht, schlaegt schon das Hochfahren
 * des Kontextes fehl.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:build/tmp/altbau-test.db",
        "tagstock.bilder-ordner=build/tmp/altbau-test-bilder",
        "tagstock.app-ordner=build/tmp/altbau-test-app",
        "tagstock.app-holen=false",
        "tagstock.quelle-beobachten=false",
        "tagstock.update-pruefen=false",
        "tagstock.registrierungs-code="})
@AutoConfigureMockMvc
class AltbauTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Laeuft vor dem Hochfahren des Kontextes und legt eine Datenbank an, wie
     * sie vor den Behaeltern aussah: artikel ohne die drei neuen Spalten.
     * Alles Weitere ergaenzt der Server selbst.
     */
    @BeforeAll
    static void datenbankAusAelteremStand() throws Exception {
        File datei = new File("build/tmp/altbau-test.db");
        if (datei.exists() && !datei.delete()) {
            throw new IllegalStateException("Testdatenbank nicht loeschbar");
        }
        //noinspection ResultOfMethodCallIgnored
        datei.getParentFile().mkdirs();
        try (Connection verbindung = DriverManager.getConnection(
                "jdbc:sqlite:" + datei.getPath());
             Statement befehl = verbindung.createStatement()) {
            befehl.executeUpdate("CREATE TABLE artikel ("
                    + "id TEXT PRIMARY KEY,"
                    + "team_id TEXT NOT NULL,"
                    + "rfid_uid TEXT,"
                    + "name TEXT NOT NULL,"
                    + "beschreibung TEXT,"
                    + "kategorie TEXT,"
                    + "standort TEXT,"
                    + "lagerort TEXT,"
                    + "bild_url TEXT,"
                    + "status TEXT NOT NULL DEFAULT 'vorhanden',"
                    + "verliehen_an TEXT,"
                    + "rueckgabe_datum INTEGER,"
                    + "zuletzt_gescannt INTEGER,"
                    + "scan_warnung TEXT NOT NULL DEFAULT '1j',"
                    + "erstellt_am INTEGER NOT NULL,"
                    + "geaendert_am INTEGER NOT NULL,"
                    + "geloescht INTEGER NOT NULL DEFAULT 0)");
            befehl.executeUpdate("CREATE INDEX idx_artikel_team ON artikel (team_id)");
            // Ein Artikel von frueher - der muss die Aktualisierung ueberleben.
            befehl.executeUpdate("INSERT INTO artikel (id, team_id, name, status,"
                    + " scan_warnung, erstellt_am, geaendert_am, geloescht)"
                    + " VALUES ('alt-1', 'team-1', 'Leiter von frueher', 'vorhanden',"
                    + " '1j', 1000, 2000, 0)");
        }
    }

    @Test
    void derServerStartetUndErgaenztWasFehlt() {
        List<String> spalten = jdbc.queryForList(
                "SELECT name FROM pragma_table_info('artikel')", String.class);
        assertTrue(spalten.contains("ist_behaelter"), "ist_behaelter fehlt: " + spalten);
        assertTrue(spalten.contains("behaelter_art"), "behaelter_art fehlt: " + spalten);
        assertTrue(spalten.contains("behaelter_kennung"), "behaelter_kennung fehlt: " + spalten);

        // Der Index auf der neuen Spalte steht auch - aber eben erst jetzt.
        List<String> indizes = jdbc.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'artikel'",
                String.class);
        assertTrue(indizes.contains("idx_artikel_behaelter"), "Index fehlt: " + indizes);

        // Und der alte Bestand ist noch da.
        assertEquals("Leiter von frueher", jdbc.queryForObject(
                "SELECT name FROM artikel WHERE id = 'alt-1'", String.class));
    }

    @Test
    void aufDerAltenDatenbankLassenSichBehaelterAnlegen() throws Exception {
        String token = senden(post("/api/v1/auth/registrieren")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"altbau@test.de\",\"name\":\"Test\","
                        + "\"passwort\":\"geheim1234\"}"), 200).get("token").asText();
        String team = senden(post("/api/v1/teams").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Keller\"}"), 200).get("id").asText();

        JsonNode box = senden(post("/api/v1/teams/" + team + "/artikel")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Kiste\",\"rfidUid\":\"K-1\",\"istBehaelter\":true,"
                        + "\"behaelterArt\":\"Kiste\"}"), 200);
        assertTrue(box.get("istBehaelter").asBoolean());

        JsonNode buch = senden(post("/api/v1/teams/" + team + "/artikel")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Buch\",\"behaelterKennung\":\"K-1\"}"), 200);
        assertEquals("K-1", buch.get("behaelterKennung").asText());
    }

    private JsonNode senden(MockHttpServletRequestBuilder anfrage, int erwarteterStatus)
            throws Exception {
        MvcResult ergebnis = mockMvc.perform(anfrage).andReturn();
        String inhalt = new String(ergebnis.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertEquals(erwarteterStatus, ergebnis.getResponse().getStatus(), "Antwort: " + inhalt);
        return inhalt.isEmpty() ? mapper.createObjectNode() : mapper.readTree(inhalt);
    }
}
