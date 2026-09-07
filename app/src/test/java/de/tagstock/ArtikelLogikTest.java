package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.ScanWarnung;
import de.tagstock.ui.BestandViewModel;

/** Prueft Warnungen, Faelligkeiten und die Filterlogik der Bestandsliste. */
public class ArtikelLogikTest {

    private Artikel artikel(String name, ArtikelStatus status) {
        Artikel artikel = new Artikel();
        artikel.name = name;
        artikel.status = status;
        return artikel;
    }

    private long vorMonaten(int monate) {
        Calendar kalender = Calendar.getInstance();
        kalender.add(Calendar.MONTH, -monate);
        return kalender.getTimeInMillis();
    }

    @Test
    public void nieGescannteGeltenAlsVermisst() {
        Artikel artikel = artikel("Leiter", ArtikelStatus.VORHANDEN);
        artikel.scanWarnung = ScanWarnung.JAHR;
        assertTrue(artikel.istVermisst());

        artikel.zuletztGescannt = System.currentTimeMillis();
        assertFalse(artikel.istVermisst());
    }

    @Test
    public void alterScanLoestWarnungAus() {
        Artikel artikel = artikel("Leiter", ArtikelStatus.VORHANDEN);
        artikel.scanWarnung = ScanWarnung.HALBJAHR;
        artikel.zuletztGescannt = vorMonaten(8);
        assertTrue(artikel.istVermisst());

        artikel.scanWarnung = ScanWarnung.ZWEI_JAHRE;
        assertFalse(artikel.istVermisst());

        artikel.scanWarnung = ScanWarnung.NIEMALS;
        artikel.zuletztGescannt = null;
        assertFalse(artikel.istVermisst());
    }

    @Test
    public void ueberfaelligNurBeiVerliehenenArtikeln() {
        Artikel artikel = artikel("Bohrer", ArtikelStatus.VERLIEHEN);
        artikel.rueckgabeDatum = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000;
        assertTrue(artikel.istUeberfaellig());
        assertFalse(artikel.istBaldFaellig());

        artikel.status = ArtikelStatus.VORHANDEN;
        assertFalse(artikel.istUeberfaellig());
    }

    @Test
    public void baldFaelligInnerhalbVonDreiTagen() {
        Artikel artikel = artikel("Bohrer", ArtikelStatus.VERLIEHEN);
        artikel.rueckgabeDatum = System.currentTimeMillis() + 2L * 24 * 60 * 60 * 1000;
        assertTrue(artikel.istBaldFaellig());
        assertFalse(artikel.istUeberfaellig());

        artikel.rueckgabeDatum = System.currentTimeMillis() + 10L * 24 * 60 * 60 * 1000;
        assertFalse(artikel.istBaldFaellig());
    }

    @Test
    public void qrWertFaelltAufDieNummerZurueck() {
        Artikel artikel = artikel("Leiter", ArtikelStatus.VORHANDEN);
        artikel.id = 42;
        assertEquals("TS-42", artikel.qrWert());

        artikel.rfidUid = "ABC123";
        assertEquals("ABC123", artikel.qrWert());
    }

    @Test
    public void sucheFindetNameKennungUndOrt() {
        Artikel artikel = artikel("Bosch Akkuschrauber", ArtikelStatus.VORHANDEN);
        artikel.rfidUid = "04E1A2";
        artikel.standort = "Werkstatt";
        artikel.lagerort = "Regal A3";
        artikel.kategorie = "Werkzeug";

        assertTrue(BestandViewModel.passt(artikel, "akku"));
        assertTrue(BestandViewModel.passt(artikel, "04e1"));
        assertTrue(BestandViewModel.passt(artikel, "werkstatt"));
        assertTrue(BestandViewModel.passt(artikel, "regal"));
        assertFalse(BestandViewModel.passt(artikel, "bohrer"));
    }

    @Test
    public void filterKombiniertStatusKategorieUndStandort() {
        Artikel werkzeug = artikel("Bohrer", ArtikelStatus.VERLIEHEN);
        werkzeug.kategorie = "Werkzeug";
        werkzeug.standort = "Werkstatt";

        Artikel elektronik = artikel("Messgerät", ArtikelStatus.VORHANDEN);
        elektronik.kategorie = "Elektronik";
        elektronik.standort = "Büro";

        List<Artikel> alle = Arrays.asList(werkzeug, elektronik);

        assertEquals(2, BestandViewModel.filtern(alle, "", null,
                BestandViewModel.ALLE, BestandViewModel.ALLE).size());
        assertEquals(1, BestandViewModel.filtern(alle, "", ArtikelStatus.VERLIEHEN,
                BestandViewModel.ALLE, BestandViewModel.ALLE).size());
        assertEquals(1, BestandViewModel.filtern(alle, "", null,
                "Elektronik", BestandViewModel.ALLE).size());
        assertEquals(0, BestandViewModel.filtern(alle, "", null,
                "Elektronik", "Werkstatt").size());
        assertEquals(1, BestandViewModel.filtern(alle, "bohrer", null,
                BestandViewModel.ALLE, BestandViewModel.ALLE).size());
    }
}
