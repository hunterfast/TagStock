package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tagstock.server.daten.GtinDaten;
import de.tagstock.server.dienst.Produktsuche;
import de.tagstock.server.modell.Produktinfo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

/** Zwischenspeicher und JSON-Griff der Produktsuche - ohne Netz. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:build/tmp/gtin-test.db",
        "tagstock.gtin-dienst=aus",
        "tagstock.registrierungs-code="})
class ProduktsucheTest {

    @Autowired
    private GtinDaten daten;

    @Autowired
    private Produktsuche suche;

    @BeforeAll
    static void datenbankLeeren() {
        File datei = new File("build/tmp/gtin-test.db");
        if (datei.exists() && !datei.delete()) {
            throw new IllegalStateException("Testdatenbank nicht loeschbar");
        }
        //noinspection ResultOfMethodCallIgnored
        datei.getParentFile().mkdirs();
    }

    @Test
    void gemerkteAngabenKommenAusDemZwischenspeicher() {
        Produktinfo info = new Produktinfo();
        info.gtin = "4006381333931";
        info.gefunden = true;
        info.name = "Kugelschreiber";
        info.marke = "Stabilo";
        info.quelle = "test";
        info.geholtAm = System.currentTimeMillis();
        daten.merken(info);

        Produktinfo geholt = suche.suchen("4006381333931");
        assertTrue(geholt.ausCache);
        assertTrue(geholt.gefunden);
        assertEquals("Kugelschreiber", geholt.name);
    }

    @Test
    void fehlschlagWirdGemerktStattJedesMalNeuGefragt() {
        Produktinfo erste = suche.suchen("4000000000006");
        assertEquals(false, erste.gefunden);
        // Der zweite Aufruf kommt aus dem Zwischenspeicher, nicht von aussen.
        assertTrue(suche.suchen("4000000000006").ausCache);
    }

    @Test
    void jsonGriffHoltDasFeldHeraus() {
        String json = "[{\"ean\":\"4006381333931\",\"name\":\"Stift \\\"blau\\\"\"}]";
        assertEquals("Stift \"blau\"", Produktsuche.feldAusJson(json, "name"));
        assertNull(Produktsuche.feldAusJson(json, "marke"));
    }
}
