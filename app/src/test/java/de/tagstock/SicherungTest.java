package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import de.tagstock.data.Bestand;
import de.tagstock.data.Code;
import de.tagstock.data.CodeType;
import de.tagstock.data.Item;
import de.tagstock.data.Lager;
import de.tagstock.data.Verleih;
import de.tagstock.util.Sicherung;

/** Export und Import muessen denselben Bestand ergeben. */
@RunWith(RobolectricTestRunner.class)
public class SicherungTest {

    private Bestand beispiel() {
        Bestand bestand = new Bestand();

        Lager lager = new Lager("Werkstatt", null, "Keller");
        lager.id = 7;
        bestand.lager.add(lager);

        Item item = new Item();
        item.id = 3;
        item.lagerId = 7;
        item.name = "Akkuschrauber";
        item.menge = 5;
        item.mengeVerloren = 1;
        item.notiz = "Ladegerät fehlt";
        bestand.items.add(item);

        bestand.codes.add(new Code(3, "4006381333931", CodeType.BARCODE));

        Verleih verleih = new Verleih();
        verleih.itemId = 3;
        verleih.person = "Max";
        verleih.menge = 2;
        verleih.ausgeliehenAm = 9000;
        bestand.verleihe.add(verleih);

        return bestand;
    }

    @Test
    public void jsonRundlaufErhaeltAlleDaten() throws JSONException {
        Bestand zurueck = Sicherung.ausJson(Sicherung.alsJson(beispiel()));

        assertEquals(1, zurueck.lager.size());
        assertEquals("Werkstatt", zurueck.lager.get(0).name);
        assertEquals(7L, zurueck.lager.get(0).id);
        // Die urspruengliche ID wird fuer den Import gemerkt.
        assertEquals(7L, zurueck.lager.get(0).importId);

        assertEquals(1, zurueck.items.size());
        Item item = zurueck.items.get(0);
        assertEquals("Akkuschrauber", item.name);
        assertEquals(5, item.menge);
        assertEquals(1, item.mengeVerloren);
        assertEquals("Ladegerät fehlt", item.notiz);
        assertNull(item.beschreibung);

        assertEquals(1, zurueck.codes.size());
        assertEquals(CodeType.BARCODE, zurueck.codes.get(0).typ);

        assertEquals(1, zurueck.verleihe.size());
        assertEquals("Max", zurueck.verleihe.get(0).person);
        assertEquals(2, zurueck.verleihe.get(0).menge);
        assertNull(zurueck.verleihe.get(0).zurueckAm);
    }

    @Test
    public void abgeschlosseneAusleihenBehaltenIhrRueckgabedatum() throws JSONException {
        Bestand bestand = beispiel();
        bestand.verleihe.get(0).zurueckAm = 12345L;

        Bestand zurueck = Sicherung.ausJson(Sicherung.alsJson(bestand));
        assertNotNull(zurueck.verleihe.get(0).zurueckAm);
        assertEquals(12345L, (long) zurueck.verleihe.get(0).zurueckAm);
    }

    @Test
    public void csvEnthaeltStueckzahlenJeZustand() {
        String csv = Sicherung.alsCsv(beispiel());
        String[] zeilen = csv.split("\n");

        assertEquals(2, zeilen.length);
        assertTrue(zeilen[0].contains("Vorhanden"));
        // Gesamt 5, davon 2 verliehen und 1 verloren -> 2 vorhanden.
        String[] felder = zeilen[1].split(";");
        assertEquals("Werkstatt", felder[0]);
        assertEquals("Akkuschrauber", felder[1]);
        assertEquals("5", felder[3]);
        assertEquals("2", felder[4]);
        assertEquals("2", felder[5]);
        assertEquals("1", felder[6]);
        assertEquals("Max", felder[7]);
    }

    @Test
    public void csvMaskiertTrennzeichen() {
        Bestand bestand = beispiel();
        bestand.items.get(0).name = "Zange; groß";

        String zeile = Sicherung.alsCsv(bestand).split("\n")[1];
        assertTrue(zeile.contains("\"Zange; groß\""));
    }

    @Test
    public void leereDateiWirdErkannt() throws JSONException {
        Bestand leer = Sicherung.ausJson("{\"version\":2}");
        assertTrue(leer.istLeer());
    }
}
