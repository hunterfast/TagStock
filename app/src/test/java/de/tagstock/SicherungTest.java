package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.Bestand;
import de.tagstock.data.Kategorie;
import de.tagstock.data.Protokoll;
import de.tagstock.data.ScanWarnung;
import de.tagstock.util.Sicherung;

/** Export und Import muessen denselben Bestand ergeben. */
@RunWith(RobolectricTestRunner.class)
public class SicherungTest {

    private Bestand beispiel() {
        Bestand bestand = new Bestand();

        Artikel artikel = new Artikel();
        artikel.id = 3;
        artikel.name = "Akkuschrauber";
        artikel.beschreibung = "Mit Ladegerät";
        artikel.kategorie = "Werkzeug";
        artikel.standort = "Werkstatt";
        artikel.lagerort = "Regal A3";
        artikel.rfidUid = "04E1A2";
        artikel.status = ArtikelStatus.VERLIEHEN;
        artikel.verliehenAn = "Max";
        artikel.rueckgabeDatum = 1800000000000L;
        artikel.zuletztGescannt = 1700000000000L;
        artikel.scanWarnung = ScanWarnung.HALBJAHR;
        bestand.artikel.add(artikel);

        bestand.kategorien.add(new Kategorie("Werkzeug", 0));

        Protokoll eintrag = new Protokoll();
        eintrag.artikelId = 3;
        eintrag.artikelName = "Akkuschrauber";
        eintrag.aktion = Protokoll.STATUS;
        eintrag.alterWert = "vorhanden";
        eintrag.neuerWert = "verliehen";
        eintrag.nutzer = "Hunter";
        eintrag.zeitpunkt = 1700000000000L;
        bestand.protokoll.add(eintrag);

        return bestand;
    }

    @Test
    public void jsonRundlaufErhaeltAlleFelder() throws JSONException {
        Bestand zurueck = Sicherung.ausJson(Sicherung.alsJson(beispiel()));

        assertEquals(1, zurueck.artikel.size());
        Artikel artikel = zurueck.artikel.get(0);
        assertEquals("Akkuschrauber", artikel.name);
        assertEquals("Werkzeug", artikel.kategorie);
        assertEquals("Werkstatt", artikel.standort);
        assertEquals("Regal A3", artikel.lagerort);
        assertEquals("04E1A2", artikel.rfidUid);
        assertEquals(ArtikelStatus.VERLIEHEN, artikel.status);
        assertEquals("Max", artikel.verliehenAn);
        assertEquals(ScanWarnung.HALBJAHR, artikel.scanWarnung);
        assertEquals(1800000000000L, (long) artikel.rueckgabeDatum);
        assertEquals(1700000000000L, (long) artikel.zuletztGescannt);

        assertEquals(1, zurueck.kategorien.size());
        assertEquals("Werkzeug", zurueck.kategorien.get(0).name);

        assertEquals(1, zurueck.protokoll.size());
        assertEquals("Hunter", zurueck.protokoll.get(0).nutzer);
    }

    @Test
    public void leereFelderBleibenLeer() throws JSONException {
        Bestand bestand = new Bestand();
        Artikel artikel = new Artikel();
        artikel.name = "Kiste";
        bestand.artikel.add(artikel);

        Artikel zurueck = Sicherung.ausJson(Sicherung.alsJson(bestand)).artikel.get(0);
        assertEquals("Kiste", zurueck.name);
        assertNull(zurueck.kategorie);
        assertNull(zurueck.rfidUid);
        assertNull(zurueck.rueckgabeDatum);
        assertEquals(ArtikelStatus.VORHANDEN, zurueck.status);
    }

    @Test
    public void csvEnthaeltDieWichtigenSpalten() {
        Context context = ApplicationProvider.getApplicationContext();
        String[] zeilen = Sicherung.alsCsv(context, beispiel()).split("\n");

        assertEquals(2, zeilen.length);
        assertTrue(zeilen[0].contains("Lagerort"));
        String[] felder = zeilen[1].split(";");
        assertEquals("Akkuschrauber", felder[0]);
        assertEquals("Werkzeug", felder[2]);
        assertEquals("Werkstatt", felder[3]);
        assertEquals("Regal A3", felder[4]);
        assertEquals("Max", felder[6]);
    }

    @Test
    public void csvMaskiertTrennzeichen() {
        Context context = ApplicationProvider.getApplicationContext();
        Bestand bestand = beispiel();
        bestand.artikel.get(0).name = "Zange; groß";

        String zeile = Sicherung.alsCsv(context, bestand).split("\n")[1];
        assertTrue(zeile.contains("\"Zange; groß\""));
    }

    @Test
    public void leereDateiWirdErkannt() throws JSONException {
        assertTrue(Sicherung.ausJson("{\"version\":3}").istLeer());
    }

    /**
     * Menge, Verpackung, Behaelter und Bild muessen eine Sicherung ueberleben.
     * Bis 2.2.0 fehlten sie: Die Sicherung vor jedem Update verlor genau
     * diese Angaben, und ein Zurueckspielen setzte jeden Bestand auf ein Stueck
     * und raeumte alle Boxen leer.
     */
    @Test
    public void mengeVerpackungUndBehaelterUeberlebenDieSicherung() throws JSONException {
        Bestand bestand = new Bestand();
        Artikel box = new Artikel();
        box.id = 1;
        box.name = "Ikea-Box blau";
        box.rfidUid = "BOX-1";
        box.istBehaelter = true;
        box.behaelterArt = "Box";
        bestand.artikel.add(box);

        Artikel shelly = new Artikel();
        shelly.id = 2;
        shelly.name = "Shelly 1 Gen4";
        shelly.menge = 5;
        shelly.istVerpackung = true;
        shelly.packungsGroesse = 4;
        shelly.angebrochen = "2,3";
        shelly.behaelterKennung = "BOX-1";
        shelly.bildUrl = "/api/v1/teams/t/bilder/b.jpg";
        bestand.artikel.add(shelly);

        Bestand zurueck = Sicherung.ausJson(Sicherung.alsJson(bestand));

        Artikel boxZurueck = zurueck.artikel.get(0);
        assertTrue(boxZurueck.istBehaelter);
        assertEquals("Box", boxZurueck.behaelterArt);

        Artikel shellyZurueck = zurueck.artikel.get(1);
        assertEquals(5, shellyZurueck.menge);
        assertTrue(shellyZurueck.istVerpackung);
        assertEquals(4, shellyZurueck.packungsGroesse);
        assertEquals("2,3", shellyZurueck.angebrochen);
        assertEquals("BOX-1", shellyZurueck.behaelterKennung);
        assertEquals("/api/v1/teams/t/bilder/b.jpg", shellyZurueck.bildUrl);
        assertEquals(25, shellyZurueck.gesamtStueck());
    }

    /** Eine Sicherung von vor 2.2.0 kennt keine Menge - dann war es ein Stueck. */
    @Test
    public void alteSicherungOhneMengeGibtEinStueck() throws JSONException {
        Artikel alt = Sicherung.ausJson("{\"artikel\":[{\"id\":1,\"name\":\"Leiter\"}]}")
                .artikel.get(0);
        assertEquals(1, alt.menge);
        assertEquals(false, alt.istVerpackung);
        assertEquals(false, alt.istBehaelter);
        assertNull(alt.behaelterKennung);
    }

    @Test
    public void csvNenntStueckzahlUndBehaelter() {
        Context context = ApplicationProvider.getApplicationContext();
        Bestand bestand = new Bestand();
        Artikel box = new Artikel();
        box.name = "Ikea-Box blau";
        box.rfidUid = "BOX-1";
        box.istBehaelter = true;
        bestand.artikel.add(box);
        Artikel shelly = new Artikel();
        shelly.name = "Shelly 1 Gen4";
        shelly.menge = 5;
        shelly.istVerpackung = true;
        shelly.packungsGroesse = 4;
        shelly.angebrochen = "2,3";
        shelly.behaelterKennung = "BOX-1";
        bestand.artikel.add(shelly);

        String[] zeilen = Sicherung.alsCsv(context, bestand).split("\n");
        String[] kopf = zeilen[0].split(";");
        String[] felder = zeilen[2].split(";");
        assertEquals("25", felder[java.util.Arrays.asList(kopf).indexOf("Stück")]);
        assertEquals("5", felder[java.util.Arrays.asList(kopf).indexOf("Packungen")]);
        assertEquals("4", felder[java.util.Arrays.asList(kopf).indexOf("Je Packung")]);
        assertEquals("Ikea-Box blau", felder[java.util.Arrays.asList(kopf).indexOf("Liegt in")]);
    }
}
