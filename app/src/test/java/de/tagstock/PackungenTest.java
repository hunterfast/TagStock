package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

import de.tagstock.data.Artikel;
import de.tagstock.data.Packungen;

/**
 * Mengen und Verpackungseinheiten. Beispiel durchweg: Shelly 1 Gen4 im
 * 4er-Pack, fuenf volle Packungen und zwei angebrochene mit 2 und 3 Stueck.
 */
public class PackungenTest {

    private Artikel shelly() {
        Artikel artikel = new Artikel();
        artikel.name = "Shelly 1 Gen4";
        artikel.menge = 5;
        artikel.istVerpackung = true;
        artikel.packungsGroesse = 4;
        artikel.angebrochen = "2,3";
        return artikel;
    }

    private Artikel kabel(int stueck) {
        Artikel artikel = new Artikel();
        artikel.name = "Patchkabel 2 m";
        artikel.menge = stueck;
        return artikel;
    }

    @Test
    public void gesamtRechnetVolleUndOffeneZusammen() {
        assertEquals(5 * 4 + 2 + 3, Packungen.gesamt(shelly()));
        assertEquals(5, Packungen.gesamt(kabel(5)));
    }

    @Test
    public void ohneVerpackungWirdSchlichtAbgezogen() {
        Artikel kabel = kabel(5);
        assertEquals(1, Packungen.entnehmen(kabel, 1));
        assertEquals(4, kabel.menge);
        assertEquals(2, Packungen.entnehmen(kabel, 2));
        assertEquals(2, kabel.menge);
    }

    @Test
    public void mehrAlsDaIstGehtNicht() {
        Artikel kabel = kabel(2);
        assertEquals(2, Packungen.entnehmen(kabel, 5));
        assertEquals(0, kabel.menge);
        assertEquals(0, Packungen.entnehmen(kabel, 1));
    }

    @Test
    public void entnahmeLeertZuerstDieOffenePackung() {
        Artikel shelly = shelly();
        assertEquals(2, Packungen.entnehmen(shelly, 2));
        // Die erste offene ist leer und faellt weg, die zweite bleibt.
        assertEquals("3", shelly.angebrochen);
        assertEquals(5, shelly.menge);
        assertEquals(23, Packungen.gesamt(shelly));
    }

    @Test
    public void istNichtsOffenWirdDieNaechstePackungAngebrochen() {
        Artikel shelly = shelly();
        shelly.angebrochen = null;
        assertEquals(20, Packungen.gesamt(shelly));

        assertEquals(1, Packungen.entnehmen(shelly, 1));
        assertEquals(4, shelly.menge);
        assertEquals("3", shelly.angebrochen);
        assertEquals(19, Packungen.gesamt(shelly));
    }

    @Test
    public void entnahmeGehtUeberMehrerePackungenHinweg() {
        Artikel shelly = shelly();
        // 2 + 3 aus den offenen, dann eine volle Packung leeren und von der
        // naechsten eines nehmen: 5 volle minus 2 angebrochene = 3.
        assertEquals(10, Packungen.entnehmen(shelly, 10));
        assertEquals(3, shelly.menge);
        assertEquals("3", shelly.angebrochen);
        assertEquals(3 * 4 + 3, Packungen.gesamt(shelly));
    }

    @Test
    public void entnahmeNimmtHoechstensWasDaIst() {
        Artikel shelly = shelly();
        assertEquals(25, Packungen.entnehmen(shelly, 99));
        assertEquals(0, Packungen.gesamt(shelly));
        assertEquals(0, shelly.menge);
        assertNull(shelly.angebrochen);
    }

    @Test
    public void zugangZaehltPackungenBeiVerpackung() {
        Artikel shelly = shelly();
        Packungen.zugang(shelly, 2);
        assertEquals(7, shelly.menge);
        assertEquals(7 * 4 + 5, Packungen.gesamt(shelly));

        Artikel kabel = kabel(5);
        Packungen.zugang(kabel, 3);
        assertEquals(8, Packungen.gesamt(kabel));
    }

    @Test
    public void eineWeiterePackungLaesstSichVonHandAnbrechen() {
        Artikel shelly = shelly();
        assertTrue(Packungen.anbrechen(shelly));
        assertEquals(4, shelly.menge);
        assertEquals(Arrays.asList(2, 3, 4), Packungen.offene(shelly.angebrochen));
        // Die Gesamtzahl aendert sich dabei nicht - es wird ja nichts entnommen.
        assertEquals(25, Packungen.gesamt(shelly));
    }

    @Test
    public void ohneVollePackungLaesstSichNichtsAnbrechen() {
        Artikel shelly = shelly();
        shelly.menge = 0;
        assertFalse(Packungen.anbrechen(shelly));
        assertFalse(Packungen.anbrechen(kabel(5)));
    }

    @Test
    public void unbrauchbareEintraegeWerdenUebergangen() {
        assertEquals(Arrays.asList(2, 3), Packungen.offene("2, ,x,3,-1,0"));
        assertNull(Packungen.alsText(Arrays.asList(0, 0)));
        assertEquals("2,3", Packungen.alsText(Arrays.asList(2, 0, 3)));
    }

    @Test
    public void geradeziehenRaeumtWidersprueckeWeg() {
        Artikel shelly = shelly();
        shelly.angebrochen = "9,1";
        Packungen.geradeziehen(shelly);
        // Mehr als in die Packung passt, kann nicht drin sein.
        assertEquals("4,1", shelly.angebrochen);

        Artikel kabel = kabel(3);
        kabel.angebrochen = "2";
        kabel.packungsGroesse = 7;
        Packungen.geradeziehen(kabel);
        assertNull(kabel.angebrochen);
        assertEquals(0, kabel.packungsGroesse);
    }
}
