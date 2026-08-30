package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.tagstock.data.Code;
import de.tagstock.data.CodeType;
import de.tagstock.data.Item;
import de.tagstock.data.ItemStatus;
import de.tagstock.data.ItemWithState;
import de.tagstock.ui.ItemListViewModel;

/** Rechnet nach, wie sich Bestand, Zustand und Filter verhalten. */
public class BestandsLogikTest {

    private ItemWithState zustand(int menge, int verliehen, int verloren, String... codes) {
        Item item = new Item();
        item.id = 1;
        item.name = "Akkuschrauber";
        item.menge = menge;
        item.mengeVerloren = verloren;

        ItemWithState state = new ItemWithState();
        state.item = item;
        state.verliehen = verliehen;
        state.codes = new ArrayList<>();
        for (String wert : codes) {
            state.codes.add(new Code(1, wert, CodeType.QR));
        }
        return state;
    }

    @Test
    public void vorhandenIstDerRest() {
        assertEquals(2, zustand(5, 2, 1).vorhanden());
        assertEquals(0, zustand(1, 1, 0).vorhanden());
        // Mehr gebunden als vorhanden darf nie negativ werden.
        assertEquals(0, zustand(1, 5, 0).vorhanden());
    }

    @Test
    public void hauptStatusZeigtDenBestimmendenZustand() {
        assertEquals(ItemStatus.VORHANDEN, zustand(3, 0, 0).hauptStatus());
        assertEquals(ItemStatus.VERLIEHEN, zustand(1, 1, 0).hauptStatus());
        assertEquals(ItemStatus.VERLOREN, zustand(1, 0, 1).hauptStatus());
        // Teils verliehen, teils da: der vorhandene Rest bestimmt die Anzeige.
        assertEquals(ItemStatus.VORHANDEN, zustand(3, 1, 0).hauptStatus());
    }

    @Test
    public void gemischterBestandWirdErkannt() {
        assertTrue(zustand(3, 1, 0).istGemischt());
        assertFalse(zustand(3, 0, 0).istGemischt());
    }

    @Test
    public void filterTrifftJedenBelegtenZustand() {
        ItemWithState gemischt = zustand(3, 1, 1);
        assertTrue(gemischt.hat(ItemStatus.VORHANDEN));
        assertTrue(gemischt.hat(ItemStatus.VERLIEHEN));
        assertTrue(gemischt.hat(ItemStatus.VERLOREN));

        ItemWithState nurDa = zustand(2, 0, 0);
        assertTrue(nurDa.hat(ItemStatus.VORHANDEN));
        assertFalse(nurDa.hat(ItemStatus.VERLIEHEN));
        assertFalse(nurDa.hat(ItemStatus.VERLOREN));
    }

    @Test
    public void sucheFindetNameUndCode() {
        ItemWithState state = zustand(1, 0, 0, "4006381333931");
        assertTrue(ItemListViewModel.Filter.passt(state, "akku"));
        assertTrue(ItemListViewModel.Filter.passt(state, "400638"));
        assertFalse(ItemListViewModel.Filter.passt(state, "bohrer"));
    }

    @Test
    public void filterKombiniertSuchtextUndZustand() {
        ItemWithState verliehen = zustand(1, 1, 0);
        ItemWithState vorhanden = zustand(1, 0, 0);
        List<ItemWithState> alle = Arrays.asList(verliehen, vorhanden);

        assertEquals(2, ItemListViewModel.Filter.anwenden(alle, "", null).size());
        assertEquals(1, ItemListViewModel.Filter.anwenden(alle, "", ItemStatus.VERLIEHEN).size());
        assertEquals(0, ItemListViewModel.Filter.anwenden(alle, "bohrer", null).size());
    }
}
