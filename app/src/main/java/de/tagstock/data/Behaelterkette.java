package de.tagstock.data;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Die Regeln fuer Behaelter an einer Stelle. Ein Behaelter ist ein Lager im
 * Lager - eine Box, eine Schublade, ein Regal -, und Behaelter duerfen
 * ineinanderstecken. Nur im Kreis darf es nicht gehen: Eine Box, die in ihrer
 * eigenen Schublade liegt, waere nirgends mehr zu finden.
 *
 * <p>Verbunden wird ueber die Kennung, nicht ueber die laufende Nummer: Die
 * Kennung klebt am Moebel, wird beim Einraeumen gescannt und bedeutet auf
 * jedem Geraet und auf dem Server dasselbe.</p>
 */
public final class Behaelterkette {

    /** Tiefer als das stapelt niemand ernsthaft - und schuetzt vor Endlosläufen. */
    private static final int MAX_TIEFE = 20;

    /** Woher die Artikel kommen, entscheidet die aufrufende Stelle. */
    public interface Nachschlag {
        @Nullable
        Artikel zuKennung(String kennung);
    }

    private Behaelterkette() {
    }

    /** true, wenn dieser Artikel etwas aufnehmen kann. */
    public static boolean nutzbar(@Nullable Artikel artikel) {
        return artikel != null && artikel.istBehaelter
                && artikel.rfidUid != null && !artikel.rfidUid.isEmpty();
    }

    /**
     * Darf "was" in "wohin"? Nein, wenn "wohin" kein Behaelter ist, wenn es
     * derselbe Artikel waere oder wenn "wohin" schon in "was" steckt.
     */
    public static boolean darfHinein(Artikel was, @Nullable Artikel wohin,
                                     Nachschlag nachschlag) {
        if (!nutzbar(wohin) || was.id == wohin.id) {
            return false;
        }
        if (wohin.rfidUid.equals(was.rfidUid)) {
            return false;
        }
        // Von "wohin" nach oben laufen: Taucht "was" auf, waere es ein Ring.
        String lauf = wohin.behaelterKennung;
        for (int tiefe = 0; lauf != null && !lauf.isEmpty() && tiefe < MAX_TIEFE; tiefe++) {
            if (lauf.equals(was.rfidUid)) {
                return false;
            }
            Artikel schritt = nachschlag.zuKennung(lauf);
            if (schritt == null) {
                return true;
            }
            if (schritt.id == was.id) {
                return false;
            }
            lauf = schritt.behaelterKennung;
        }
        return true;
    }

    /**
     * Alle Behaelter, in die dieser Artikel darf - fuer die Auswahl im
     * Formular. Ausgelassen wird er selbst und alles, was schon in ihm steckt.
     * Nach Namen sortiert.
     */
    public static List<Artikel> moegliche(List<Artikel> alle, @Nullable Artikel eigener) {
        Set<String> tabu = new HashSet<>();
        if (eigener != null && eigener.rfidUid != null && !eigener.rfidUid.isEmpty()) {
            Deque<String> offen = new ArrayDeque<>();
            offen.push(eigener.rfidUid);
            while (!offen.isEmpty()) {
                String kennung = offen.pop();
                if (!tabu.add(kennung)) {
                    continue;
                }
                for (Artikel eintrag : alle) {
                    if (kennung.equals(eintrag.behaelterKennung)
                            && eintrag.rfidUid != null && !eintrag.rfidUid.isEmpty()) {
                        offen.push(eintrag.rfidUid);
                    }
                }
            }
        }
        List<Artikel> treffer = new ArrayList<>();
        for (Artikel eintrag : alle) {
            if (nutzbar(eintrag) && !tabu.contains(eintrag.rfidUid)
                    && (eigener == null || eintrag.id != eigener.id)) {
                treffer.add(eintrag);
            }
        }
        treffer.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return treffer;
    }
}
