package de.tagstock.data;

import androidx.annotation.Nullable;
import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.List;

/**
 * Artikel samt zugehoerigen Codes und der Anzahl gerade verliehener Stuecke.
 * Vorhanden, verliehen und verloren sind Stueckzahlen - ein Artikel kann also
 * gleichzeitig teils verliehen und teils vorhanden sein.
 */
public class ItemWithState {

    @Embedded
    public Item item;

    @Relation(parentColumn = "id", entityColumn = "itemId")
    public List<Code> codes = new ArrayList<>();

    /** Summe der offenen Ausleihen, wird von der Abfrage geliefert. */
    public int verliehen;

    public int verloren() {
        return item.mengeVerloren;
    }

    public int vorhanden() {
        return Math.max(0, item.menge - verliehen - item.mengeVerloren);
    }

    /** true, wenn mindestens ein Stueck in diesem Zustand ist. */
    public boolean hat(ItemStatus status) {
        switch (status) {
            case VERLIEHEN:
                return verliehen > 0;
            case VERLOREN:
                return verloren() > 0;
            default:
                return vorhanden() > 0;
        }
    }

    /** Zustand, der die Plakette in der Liste faerbt. */
    public ItemStatus hauptStatus() {
        if (verliehen > 0 && vorhanden() == 0 && verloren() == 0) {
            return ItemStatus.VERLIEHEN;
        }
        if (verloren() > 0 && vorhanden() == 0 && verliehen == 0) {
            return ItemStatus.VERLOREN;
        }
        if (vorhanden() > 0) {
            return ItemStatus.VORHANDEN;
        }
        return verliehen > 0 ? ItemStatus.VERLIEHEN : ItemStatus.VERLOREN;
    }

    /** true, wenn sich der Bestand auf mehrere Zustaende verteilt. */
    public boolean istGemischt() {
        int zustaende = 0;
        if (vorhanden() > 0) zustaende++;
        if (verliehen > 0) zustaende++;
        if (verloren() > 0) zustaende++;
        return zustaende > 1;
    }

    /** Erster hinterlegter Code oder null. */
    @Nullable
    public Code ersterCode() {
        return codes == null || codes.isEmpty() ? null : codes.get(0);
    }

    public boolean hatCode(String wert) {
        if (codes == null || wert == null) {
            return false;
        }
        for (Code code : codes) {
            if (wert.equals(code.wert)) {
                return true;
            }
        }
        return false;
    }
}
