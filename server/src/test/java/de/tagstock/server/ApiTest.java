package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.UUID;

/** Durchlauf durch die API: Konto, Team, Bestand, Abgleich, Anfragen. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:build/tmp/api-test.db",
        "tagstock.bilder-ordner=build/tmp/api-test-bilder",
        "tagstock.app-ordner=build/tmp/api-test-app",
        "tagstock.app-holen=false",
        "tagstock.update-pruefen=false",
        "tagstock.registrierungs-code="})
@AutoConfigureMockMvc
class ApiTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void datenbankLeeren() {
        ordnerLeeren(new File("build/tmp/api-test-bilder"));
        File datei = new File("build/tmp/api-test.db");
        if (datei.exists() && !datei.delete()) {
            throw new IllegalStateException("Testdatenbank nicht loeschbar");
        }
        //noinspection ResultOfMethodCallIgnored
        datei.getParentFile().mkdirs();
    }

    private static void ordnerLeeren(File ordner) {
        File[] inhalt = ordner.listFiles();
        if (inhalt != null) {
            for (File eintrag : inhalt) {
                ordnerLeeren(eintrag);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        ordner.delete();
    }

    private JsonNode senden(MockHttpServletRequestBuilder anfrage, int erwarteterStatus)
            throws Exception {
        MvcResult ergebnis = mockMvc.perform(anfrage).andReturn();
        assertEquals(erwarteterStatus, ergebnis.getResponse().getStatus(),
                "Antwort: " + new String(ergebnis.getResponse().getContentAsByteArray(),
                        StandardCharsets.UTF_8));
        // Bewusst als UTF-8 lesen: sonst deutet MockMvc die Umlaute falsch.
        String inhalt = new String(ergebnis.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
        return inhalt.isEmpty() ? mapper.createObjectNode() : mapper.readTree(inhalt);
    }

    private MockHttpServletRequestBuilder mitToken(MockHttpServletRequestBuilder anfrage,
                                                   String token) {
        return anfrage.header("Authorization", "Bearer " + token);
    }

    private String neuesKonto(String email) throws Exception {
        JsonNode antwort = senden(post("/api/v1/auth/registrieren")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"name\":\"Test\","
                        + "\"passwort\":\"geheim1234\"}"), 200);
        return antwort.get("token").asText();
    }

    private String neuesTeam(String token, String name) throws Exception {
        JsonNode team = senden(mitToken(post("/api/v1/teams"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"), 200);
        return team.get("id").asText();
    }

    private String neuerArtikel(String token, String teamId, String name, String kennung)
            throws Exception {
        String koerper = kennung == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"rfidUid\":\"" + kennung + "\"}";
        JsonNode artikel = senden(mitToken(post("/api/v1/teams/" + teamId + "/artikel"), token)
                .contentType(MediaType.APPLICATION_JSON).content(koerper), 200);
        return artikel.get("id").asText();
    }

    /** Kleinstes JPEG, das dem Server als Bild durchgeht. */
    private byte[] jpeg(byte fuellung) {
        byte[] daten = new byte[64];
        daten[0] = (byte) 0xFF;
        daten[1] = (byte) 0xD8;
        daten[2] = (byte) 0xFF;
        for (int i = 3; i < daten.length; i++) {
            daten[i] = fuellung;
        }
        return daten;
    }

    private String email() {
        return UUID.randomUUID().toString().substring(0, 8) + "@test.de";
    }

    // -------------------------------------------------------------------- Tests

    @Test
    void statusLaeuftOhneAnmeldung() throws Exception {
        JsonNode status = senden(get("/api/v1/status"), 200);
        assertTrue(status.get("bereit").asBoolean());
        assertEquals(1, status.get("apiVersion").asInt());
    }

    @Test
    void ohneTokenKeinZugriff() throws Exception {
        senden(get("/api/v1/ich"), 401);
    }

    @Test
    void kontoAnlegenUndAnmelden() throws Exception {
        String email = email();
        String token = neuesKonto(email);
        assertNotNull(token);

        JsonNode ich = senden(mitToken(get("/api/v1/ich"), token), 200);
        assertEquals(email, ich.get("benutzer").get("email").asText());
        // Das Passwort darf nie in einer Antwort auftauchen.
        assertTrue(ich.get("benutzer").get("passwort") == null);

        JsonNode angemeldet = senden(post("/api/v1/auth/anmelden")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"passwort\":\"geheim1234\"}"), 200);
        assertNotNull(angemeldet.get("token").asText());

        senden(post("/api/v1/auth/anmelden")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"passwort\":\"falsch12345\"}"), 401);
    }

    @Test
    void teamBekommtStandardkategorien() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Werkstatt");

        JsonNode kategorien = senden(
                mitToken(get("/api/v1/teams/" + teamId + "/kategorien"), token), 200);
        assertEquals(8, kategorien.size());
    }

    @Test
    void artikelAnlegenUndKennungIstEindeutig() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Keller");
        neuerArtikel(token, teamId, "Akkuschrauber", "ABC123");

        // Dieselbe Kennung ein zweites Mal wird abgelehnt.
        senden(mitToken(post("/api/v1/teams/" + teamId + "/artikel"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Zweitgerät\",\"rfidUid\":\"ABC123\"}"), 409);

        JsonNode treffer = senden(mitToken(
                get("/api/v1/teams/" + teamId + "/artikel/suche?kennung=ABC123"), token), 200);
        assertEquals("Akkuschrauber", treffer.get("name").asText());
    }

    @Test
    void aenderungenLandenImProtokoll() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Büro");
        String artikelId = neuerArtikel(token, teamId, "Beamer", null);

        senden(mitToken(put("/api/v1/teams/" + teamId + "/artikel/" + artikelId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Beamer\",\"status\":\"verliehen\","
                        + "\"verliehenAn\":\"Max\",\"standort\":\"Konferenzraum\"}"), 200);

        JsonNode protokoll = senden(mitToken(
                get("/api/v1/teams/" + teamId + "/artikel/" + artikelId + "/protokoll"), token), 200);
        boolean status = false;
        boolean standort = false;
        for (JsonNode eintrag : protokoll) {
            if ("Status geändert".equals(eintrag.get("aktion").asText())) {
                status = true;
            }
            if ("Standort geändert".equals(eintrag.get("aktion").asText())) {
                standort = true;
            }
        }
        assertTrue(status, "Statuswechsel fehlt");
        assertTrue(standort, "Standortwechsel fehlt");
    }

    @Test
    void abgleichLiefertAenderungenUndNimmtNeueAn() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Halle");
        neuerArtikel(token, teamId, "Leiter", "LEIT1");

        JsonNode alles = senden(mitToken(get("/api/v1/teams/" + teamId + "/sync?seit=0"), token), 200);
        assertEquals(1, alles.get("artikel").size());
        long stand = alles.get("stand").asLong();

        // Die App laedt einen neuen Artikel hoch und bekommt seine Server-ID.
        JsonNode antwort = senden(mitToken(post("/api/v1/teams/" + teamId + "/sync?seit=" + stand),
                token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"artikel\":[{\"lokaleId\":7,\"name\":\"Hammer\","
                        + "\"rfidUid\":\"HAM1\",\"geaendertAm\":" + System.currentTimeMillis()
                        + "}],\"protokoll\":[]}"), 200);

        JsonNode zuordnung = antwort.get("zuordnungen").get(0);
        assertTrue(zuordnung.get("angenommen").asBoolean());
        assertEquals(7, zuordnung.get("lokaleId").asInt());
        assertNotNull(zuordnung.get("serverId").asText());
    }

    @Test
    void abgleichLehntBelegteKennungAb() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Lager");
        neuerArtikel(token, teamId, "Bohrer", "DUP1");

        JsonNode antwort = senden(mitToken(post("/api/v1/teams/" + teamId + "/sync"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artikel\":[{\"lokaleId\":9,\"name\":\"Anderer Bohrer\","
                        + "\"rfidUid\":\"DUP1\",\"geaendertAm\":" + System.currentTimeMillis()
                        + "}],\"protokoll\":[]}"), 200);

        JsonNode zuordnung = antwort.get("zuordnungen").get(0);
        assertTrue(!zuordnung.get("angenommen").asBoolean());
        assertTrue(zuordnung.get("grund").asText().contains("Kennung"));
    }

    @Test
    void abgleichLaesstAeltereFassungLiegen() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Archiv");
        String artikelId = neuerArtikel(token, teamId, "Kiste", null);

        JsonNode antwort = senden(mitToken(post("/api/v1/teams/" + teamId + "/sync"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artikel\":[{\"id\":\"" + artikelId + "\",\"lokaleId\":3,"
                        + "\"name\":\"Alte Kiste\",\"geaendertAm\":1000}],\"protokoll\":[]}"), 200);

        JsonNode zuordnung = antwort.get("zuordnungen").get(0);
        assertTrue(!zuordnung.get("angenommen").asBoolean());
        assertEquals("Server ist neuer", zuordnung.get("grund").asText());
    }

    @Test
    void einladungUndRollenSteuernDenZugriff() throws Exception {
        String adminToken = neuesKonto(email());
        String teamId = neuesTeam(adminToken, "Verein");

        JsonNode einladung = senden(mitToken(post("/api/v1/teams/" + teamId + "/einladung"),
                adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rolle\":\"mitglied\"}"), 200);
        String code = einladung.get("code").asText();

        String gastToken = neuesKonto(email());
        // Ohne Mitgliedschaft ist der Bestand tabu.
        senden(mitToken(get("/api/v1/teams/" + teamId + "/artikel"), gastToken), 403);

        JsonNode team = senden(mitToken(post("/api/v1/teams/beitreten"), gastToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"), 200);
        assertEquals("mitglied", team.get("rolle").asText());

        // Lesen ja, aendern nein.
        senden(mitToken(get("/api/v1/teams/" + teamId + "/artikel"), gastToken), 200);
        senden(mitToken(post("/api/v1/teams/" + teamId + "/artikel"), gastToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Heimlich\"}"), 403);
    }

    @Test
    void letzterAdminBleibtAdmin() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Solo");
        JsonNode ich = senden(mitToken(get("/api/v1/ich"), token), 200);
        String benutzerId = ich.get("benutzer").get("id").asText();

        senden(mitToken(put("/api/v1/teams/" + teamId + "/mitglieder/" + benutzerId), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rolle\":\"mitglied\"}"), 409);
        senden(mitToken(delete("/api/v1/teams/" + teamId + "/mitglieder/" + benutzerId), token), 409);
    }

    @Test
    void anfrageWirdGenehmigtUndSetztDenArtikelAufVerliehen() throws Exception {
        String adminToken = neuesKonto(email());
        String teamId = neuesTeam(adminToken, "Fundus");
        String artikelId = neuerArtikel(adminToken, teamId, "Zelt", null);

        JsonNode einladung = senden(mitToken(post("/api/v1/teams/" + teamId + "/einladung"),
                adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rolle\":\"mitglied\"}"), 200);
        String gastToken = neuesKonto(email());
        senden(mitToken(post("/api/v1/teams/beitreten"), gastToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + einladung.get("code").asText() + "\"}"), 200);

        JsonNode anfrage = senden(mitToken(post("/api/v1/teams/" + teamId + "/anfragen"), gastToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"artikelId\":\"" + artikelId + "\",\"nachricht\":\"Fürs Wochenende\","
                        + "\"datumBis\":1800000000000}"), 200);
        assertEquals("offen", anfrage.get("status").asText());
        String anfrageId = anfrage.get("id").asText();

        // Ein Mitglied darf nicht selbst entscheiden.
        senden(mitToken(post("/api/v1/teams/" + teamId + "/anfragen/" + anfrageId + "/entscheiden"),
                gastToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"genehmigt\":true}"), 403);

        JsonNode entschieden = senden(mitToken(
                post("/api/v1/teams/" + teamId + "/anfragen/" + anfrageId + "/entscheiden"),
                adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"genehmigt\":true,\"antwort\":\"Viel Spaß\"}"), 200);
        assertEquals("genehmigt", entschieden.get("status").asText());

        JsonNode artikel = senden(mitToken(get("/api/v1/teams/" + teamId + "/artikel"), adminToken), 200);
        JsonNode zelt = null;
        for (JsonNode eintrag : artikel) {
            if (artikelId.equals(eintrag.get("id").asText())) {
                zelt = eintrag;
            }
        }
        assertNotNull(zelt);
        assertEquals("verliehen", zelt.get("status").asText());
        assertEquals(1800000000000L, zelt.get("rueckgabeDatum").asLong());

        // Ein zweites Mal entscheiden geht nicht.
        senden(mitToken(post("/api/v1/teams/" + teamId + "/anfragen/" + anfrageId + "/entscheiden"),
                adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"genehmigt\":false}"), 409);
    }

    @Test
    void geloeschteArtikelKommenAlsMerkmalZurueck() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Abstellraum");
        String artikelId = neuerArtikel(token, teamId, "Alte Lampe", "LAMP1");

        senden(mitToken(delete("/api/v1/teams/" + teamId + "/artikel/" + artikelId), token), 200);

        JsonNode liste = senden(mitToken(get("/api/v1/teams/" + teamId + "/artikel"), token), 200);
        assertEquals(0, liste.size());

        JsonNode abgleich = senden(mitToken(get("/api/v1/teams/" + teamId + "/sync?seit=0"), token), 200);
        assertEquals(1, abgleich.get("artikel").size());
        assertTrue(abgleich.get("artikel").get(0).get("geloescht").asBoolean());

        // Die Kennung ist danach wieder frei.
        neuerArtikel(token, teamId, "Neue Lampe", "LAMP1");
    }

    @Test
    void bildLiegtAufDemServerUndKommtZurueck() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Werkstatt");
        String artikelId = neuerArtikel(token, teamId, "Bohrmaschine", null);

        byte[] inhalt = jpeg((byte) 7);
        JsonNode abgelegt = senden(mitToken(
                post("/api/v1/teams/" + teamId + "/artikel/" + artikelId + "/bild"), token)
                .contentType(MediaType.IMAGE_JPEG).content(inhalt), 200);
        String bildUrl = abgelegt.get("bildUrl").asText();
        assertTrue(bildUrl.startsWith("/api/v1/teams/" + teamId + "/bilder/"), bildUrl);

        // Der Artikel zeigt jetzt auf das Bild - so findet es jedes Geraet.
        JsonNode liste = senden(mitToken(get("/api/v1/teams/" + teamId + "/artikel"), token), 200);
        assertEquals(bildUrl, liste.get(0).get("bildUrl").asText());

        MvcResult bild = mockMvc.perform(mitToken(get(bildUrl), token)).andReturn();
        assertEquals(200, bild.getResponse().getStatus());
        assertEquals(MediaType.IMAGE_JPEG_VALUE, bild.getResponse().getContentType());
        assertArrayEquals(inhalt, bild.getResponse().getContentAsByteArray());

        // Ohne Anmeldung gibt der Server das Bild nicht heraus.
        assertEquals(401, mockMvc.perform(get(bildUrl)).andReturn().getResponse().getStatus());
    }

    @Test
    void neuesBildErsetztDasAlteUndLoeschenRaeumtAuf() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Halle");
        String artikelId = neuerArtikel(token, teamId, "Leiter", null);
        String pfad = "/api/v1/teams/" + teamId + "/artikel/" + artikelId + "/bild";

        String erstes = senden(mitToken(post(pfad), token)
                .contentType(MediaType.IMAGE_JPEG).content(jpeg((byte) 1)), 200)
                .get("bildUrl").asText();
        String zweites = senden(mitToken(post(pfad), token)
                .contentType(MediaType.IMAGE_JPEG).content(jpeg((byte) 2)), 200)
                .get("bildUrl").asText();

        assertNotEquals(erstes, zweites);
        assertEquals(404, mockMvc.perform(mitToken(get(erstes), token))
                .andReturn().getResponse().getStatus());

        senden(mitToken(delete(pfad), token), 200);
        assertEquals(404, mockMvc.perform(mitToken(get(zweites), token))
                .andReturn().getResponse().getStatus());
        JsonNode liste = senden(mitToken(get("/api/v1/teams/" + teamId + "/artikel"), token), 200);
        assertTrue(liste.get(0).get("bildUrl").isNull());
    }

    @Test
    void andereDateienNimmtDerServerNichtAn() throws Exception {
        String token = neuesKonto(email());
        String teamId = neuesTeam(token, "Lager Süd");
        String artikelId = neuerArtikel(token, teamId, "Kabeltrommel", null);

        senden(mitToken(post("/api/v1/teams/" + teamId + "/artikel/" + artikelId + "/bild"), token)
                .contentType(MediaType.IMAGE_JPEG)
                .content("kein Bild, nur Text".getBytes(StandardCharsets.UTF_8)), 400);
    }

    @Test
    void mitgliederDuerfenBilderSehenAberNichtAendern() throws Exception {
        String adminToken = neuesKonto(email());
        String teamId = neuesTeam(adminToken, "Bühne");
        String artikelId = neuerArtikel(adminToken, teamId, "Stativ", null);
        String bildUrl = senden(mitToken(
                post("/api/v1/teams/" + teamId + "/artikel/" + artikelId + "/bild"), adminToken)
                .contentType(MediaType.IMAGE_JPEG).content(jpeg((byte) 3)), 200)
                .get("bildUrl").asText();

        String code = senden(mitToken(post("/api/v1/teams/" + teamId + "/einladung"), adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rolle\":\"mitglied\"}"), 200)
                .get("code").asText();
        String gastToken = neuesKonto(email());
        senden(mitToken(post("/api/v1/teams/beitreten"), gastToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"), 200);

        assertEquals(200, mockMvc.perform(mitToken(get(bildUrl), gastToken))
                .andReturn().getResponse().getStatus());
        senden(mitToken(post("/api/v1/teams/" + teamId + "/artikel/" + artikelId + "/bild"),
                gastToken).contentType(MediaType.IMAGE_JPEG).content(jpeg((byte) 4)), 403);
    }

    @Test
    void passwortHashVerlaesstDenServerNie() throws Exception {
        String email = email();
        JsonNode registriert = senden(post("/api/v1/auth/registrieren")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"name\":\"Test\","
                        + "\"passwort\":\"geheim1234\"}"), 200);
        assertTrue(registriert.get("benutzer").get("passwort") == null
                || registriert.get("benutzer").get("passwort").isNull(),
                "Antwort: " + registriert);

        String token = registriert.get("token").asText();
        JsonNode ich = senden(mitToken(get("/api/v1/ich"), token), 200);
        assertTrue(ich.get("benutzer").get("passwort") == null
                || ich.get("benutzer").get("passwort").isNull(), "Antwort: " + ich);
    }

    @Test
    void gtinNachschlagenBrauchtEinenDienst() throws Exception {
        String token = neuesKonto(email());

        // Ohne Anmeldung gar nichts.
        senden(get("/api/v1/gtin/4006381333931"), 401);
        // Keine gueltige Nummer.
        senden(mitToken(get("/api/v1/gtin/abc"), token), 400);
        // In den Tests ist kein Dienst eingerichtet - der Server sagt das deutlich.
        senden(mitToken(get("/api/v1/gtin/4006381333931"), token), 503);

        JsonNode status = senden(get("/api/v1/status"), 200);
        assertEquals(false, status.get("gtinDienst").asBoolean());
    }

    @Test
    void serverNenntSeinenStandUndKenntKeineNeuereFassung() throws Exception {
        JsonNode status = senden(get("/api/v1/status"), 200);
        assertTrue(status.get("version").asText().length() > 0);
        assertNotNull(status.get("gebautAm"));

        String token = neuesKonto(email());
        senden(get("/api/v1/aktualisierung"), 401);
        JsonNode stand = senden(mitToken(get("/api/v1/aktualisierung"), token), 200);
        // In den Tests ist die Pruefung aus - dann wird auch nichts gemeldet.
        assertEquals(false, stand.get("geprueft").asBoolean());
        assertEquals(false, stand.get("aktualisierungVerfuegbar").asBoolean());
    }

    @Test
    void appWirdVomServerVerteiltSobaldSieDaLiegt() throws Exception {
        // Aus einem frueheren Lauf darf hier nichts liegen bleiben.
        ordnerLeeren(new File("build/tmp/api-test-app"));
        File fach = new File("build/tmp/api-test-app/aktuell");
        //noinspection ResultOfMethodCallIgnored
        fach.mkdirs();
        File apk = new File(fach, "tagstock.apk");

        JsonNode ohne = senden(get("/api/v1/app"), 200);
        assertTrue(ohne.get("aktuell").isNull());
        senden(get("/api/v1/app/tagstock.apk"), 404);

        java.nio.file.Files.write(apk.toPath(), new byte[]{1, 2, 3, 4});
        java.nio.file.Files.write(new File(fach, "app.json").toPath(),
                "{\"versionCode\": 7, \"versionName\": \"2.1\"}"
                        .getBytes(StandardCharsets.UTF_8));

        JsonNode mit = senden(get("/api/v1/app"), 200);
        assertEquals(7, mit.get("aktuell").get("versionCode").asInt());
        assertEquals("2.1", mit.get("aktuell").get("versionName").asText());
        assertTrue(mit.get("vorher").isNull());

        MvcResult datei = mockMvc.perform(get("/api/v1/app/aktuell/tagstock.apk")).andReturn();
        assertEquals(200, datei.getResponse().getStatus());
        assertArrayEquals(new byte[]{1, 2, 3, 4},
                datei.getResponse().getContentAsByteArray());

        // Die vorherige Fassung ist die Rueckfallebene, wenn etwas schiefgeht.
        File alt = new File("build/tmp/api-test-app/vorher");
        //noinspection ResultOfMethodCallIgnored
        alt.mkdirs();
        java.nio.file.Files.write(new File(alt, "tagstock.apk").toPath(), new byte[]{9, 9});
        java.nio.file.Files.write(new File(alt, "app.json").toPath(),
                "{\"versionCode\": 6, \"versionName\": \"2.0\"}"
                        .getBytes(StandardCharsets.UTF_8));
        JsonNode beide = senden(get("/api/v1/app"), 200);
        assertEquals(6, beide.get("vorher").get("versionCode").asInt());
        assertEquals(200, mockMvc.perform(get("/api/v1/app/vorher/tagstock.apk"))
                .andReturn().getResponse().getStatus());

        // Nachsehen darf nur, wer angemeldet ist.
        senden(post("/api/v1/app/pruefen"), 401);
        String token = neuesKonto(email());
        senden(mitToken(post("/api/v1/app/pruefen"), token), 200);
    }

}
