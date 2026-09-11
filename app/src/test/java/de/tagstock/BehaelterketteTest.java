package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.tagstock.data.Artikel;
import de.tagstock.data.Behaelterkette;

/**
 * Behaelter sind Lager im Lager und duerfen ineinanderstecken - nur nicht im
 * Kreis. Hier steht, wo die Grenze liegt: Aufbau ist eine Box, darin eine
 * Schublade, darin eine Zange.
 */
public class BehaelterketteTest {

    private final Artikel box = behaelter(1, "Ikea-Box blau", "BOX-1", null);
    private final Artikel schublade = behaelter(2, "Schublade oben", "SCHUB-1", "BOX-1");
    private final Artikel zange = dings(3, "Zange", "WZ-1", "SCHUB-1");
    private final Artikel akku = dings(4, "Akkuschrauber", "AS-1", null);
    private final List<Artikel> alle = new ArrayList<>(
            Arrays.asList(box, schublade, zange, akku));

    private Artikel dings(long id, String name, String kennung, String liegtIn) {
        Artikel artikel = new Artikel();
        artikel.id = id;
        artikel.name = name;
        artikel.rfidUid = kennung;
        artikel.behaelterKennung = liegtIn;
        return artikel;
    }

    private Artikel behaelter(long id, String name, String kennung, String liegtIn) {
        Artikel artikel = dings(id, name, kennung, liegtIn);
        artikel.istBehaelter = true;
        return artikel;
    }

    private Artikel nachKennung(String kennung) {
        for (Artikel artikel : alle) {
            if (kennung.equals(artikel.rfidUid)) {
                return artikel;
            }
        }
        return null;
    }

    @Test
    public void ohneEigeneKennungIstNichtsEinBehaelter() {
        Artikel ohne = behaelter(9, "Kiste ohne Schild", null, null);
        assertFalse(Behaelterkette.nutzbar(ohne));
        assertTrue(Behaelterkette.nutzbar(box));
    }

    @Test
    public void inEinenNichtBehaelterGehtNichts() {
        assertFalse(Behaelterkette.darfHinein(akku, zange, this::nachKennung));
    }

    @Test
    public void gewoehnlichesEinraeumenGehtDurch() {
        assertTrue(Behaelterkette.darfHinein(akku, box, this::nachKennung));
        assertTrue(Behaelterkette.darfHinein(akku, schublade, this::nachKennung));
    }

    @Test
    public void nichtsLiegtInSichSelbst() {
        assertFalse(Behaelterkette.darfHinein(box, box, this::nachKennung));
    }

    @Test
    public void keinRingUeberEcken() {
        // Die Box in ihre eigene Schublade - das ginge nie wieder auf.
        assertFalse(Behaelterkette.darfHinein(box, schublade, this::nachKennung));
    }

    @Test
    public void auswahlLaesstWegWasEinenRingErgaebe() {
        // Fuer die Box bleibt nichts uebrig: Der einzige andere Behaelter
        // liegt schon in ihr.
        assertTrue(Behaelterkette.moegliche(alle, box).isEmpty());

        // Die Schublade darf zurueck in die Box.
        List<Artikel> fuerSchublade = Behaelterkette.moegliche(alle, schublade);
        assertEquals(1, fuerSchublade.size());
        assertEquals("Ikea-Box blau", fuerSchublade.get(0).name);

        // Ein gewoehnlicher Gegenstand darf ueberall hin.
        assertEquals(2, Behaelterkette.moegliche(alle, akku).size());

        // Und ein noch nicht angelegter Artikel auch.
        assertEquals(2, Behaelterkette.moegliche(alle, null).size());
    }

    @Test
    public void auswahlStehtNachNamenSortiert() {
        List<Artikel> moegliche = Behaelterkette.moegliche(alle, akku);
        assertEquals("Ikea-Box blau", moegliche.get(0).name);
        assertEquals("Schublade oben", moegliche.get(1).name);
    }

    @Test
    public void eineKetteOhneEndeHaeltNichtAuf() {
        // Zwei Behaelter, die sich gegenseitig nennen - darf nicht haengen.
        Artikel a = behaelter(10, "A", "A", "B");
        Artikel b = behaelter(11, "B", "B", "A");
        alle.clear();
        alle.add(a);
        alle.add(b);
        assertFalse(Behaelterkette.darfHinein(a, b, this::nachKennung));
        assertTrue(Behaelterkette.moegliche(alle, a).isEmpty());
    }
}
