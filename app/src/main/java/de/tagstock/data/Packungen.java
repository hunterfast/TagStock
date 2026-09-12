package de.tagstock.data;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rechnen mit Mengen und Verpackungseinheiten.
 *
 * <p>Ohne Verpackungseinheit ist {@code menge} schlicht die Stueckzahl. Mit
 * einer zaehlt {@code menge} die <b>vollen, ungeoeffneten</b> Packungen; was
 * aus angebrochenen Packungen noch uebrig ist, steht einzeln daneben. Einzeln
 * deshalb, weil im Regal durchaus versehentlich eine zweite Packung
 * aufgerissen wird - dann sind eben zwei offen, mit je eigenem Rest.</p>
 *
 * <pre>
 * menge = 5, packungsGroesse = 4, offene = [2, 3]  ->  5*4 + 2 + 3 = 25 Stueck
 * </pre>
 *
 * <p>Die offenen Packungen stehen als Text "2,3" in einer Spalte. Das ist
 * kurz, wandert unveraendert durch den Abgleich und braucht keine eigene
 * Tabelle - eine Handvoll Zahlen je Artikel rechtfertigt keine.</p>
 */
public final class Packungen {

    /** Mehr offene Packungen desselben Artikels ergeben keinen Sinn. */
    public static final int HOECHSTENS_OFFEN = 20;

    private Packungen() {
    }

    /** Die Reste der offenen Packungen aus der gespeicherten Form. */
    public static List<Integer> offene(@Nullable String gespeichert) {
        List<Integer> reste = new ArrayList<>();
        if (gespeichert == null || gespeichert.trim().isEmpty()) {
            return reste;
        }
        for (String teil : gespeichert.split(",")) {
            String sauber = teil.trim();
            if (sauber.isEmpty()) {
                continue;
            }
            try {
                int wert = Integer.parseInt(sauber);
                if (wert > 0) {
                    reste.add(wert);
                }
            } catch (NumberFormatException unbrauchbar) {
                // Eine unlesbare Zahl wird stillschweigend uebergangen; sie
                // darf nicht den ganzen Artikel unbrauchbar machen.
            }
            if (reste.size() >= HOECHSTENS_OFFEN) {
                break;
            }
        }
        return reste;
    }

    /** Zurueck in die gespeicherte Form; nichts Offenes ergibt null. */
    @Nullable
    public static String alsText(List<Integer> reste) {
        StringBuilder text = new StringBuilder();
        for (int rest : reste) {
            if (rest <= 0) {
                continue;
            }
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(rest);
        }
        return text.length() == 0 ? null : text.toString();
    }

    /** Wie viele Stueck insgesamt - volle Packungen und Reste zusammen. */
    public static int gesamt(Artikel artikel) {
        if (!artikel.istVerpackung || artikel.packungsGroesse <= 0) {
            return Math.max(0, artikel.menge);
        }
        int summe = Math.max(0, artikel.menge) * artikel.packungsGroesse;
        for (int rest : offene(artikel.angebrochen)) {
            summe += rest;
        }
        return summe;
    }

    /**
     * Entnimmt Stueck. Bedient sich zuerst aus der zuerst geoeffneten
     * Packung; ist die leer, wird die naechste angebrochen - genau wie im
     * Regal. Gibt zurueck, wie viele tatsaechlich entnommen wurden; mehr als
     * vorhanden ist geht nicht.
     */
    public static int entnehmen(Artikel artikel, int stueck) {
        if (stueck <= 0) {
            return 0;
        }
        if (!artikel.istVerpackung || artikel.packungsGroesse <= 0) {
            int moeglich = Math.min(stueck, Math.max(0, artikel.menge));
            artikel.menge -= moeglich;
            return moeglich;
        }
        List<Integer> offene = offene(artikel.angebrochen);
        int genommen = 0;
        while (genommen < stueck) {
            if (offene.isEmpty()) {
                if (artikel.menge <= 0) {
                    break;
                }
                // Nichts mehr offen: die naechste Packung anbrechen.
                artikel.menge--;
                offene.add(artikel.packungsGroesse);
            }
            int rest = offene.get(0);
            int nehmen = Math.min(rest, stueck - genommen);
            rest -= nehmen;
            genommen += nehmen;
            if (rest == 0) {
                offene.remove(0);
            } else {
                offene.set(0, rest);
            }
        }
        artikel.angebrochen = alsText(offene);
        return genommen;
    }

    /**
     * Zugang. Ohne Verpackungseinheit sind es Stueck, mit einer ganze
     * Packungen - so kauft man ein.
     */
    public static void zugang(Artikel artikel, int anzahl) {
        if (anzahl <= 0) {
            return;
        }
        artikel.menge = Math.max(0, artikel.menge) + anzahl;
    }

    /**
     * Eine weitere Packung anbrechen, ohne etwas zu entnehmen - fuer den
     * Fall, dass im Regal versehentlich eine zweite aufgerissen wurde.
     */
    public static boolean anbrechen(Artikel artikel) {
        if (!artikel.istVerpackung || artikel.packungsGroesse <= 0 || artikel.menge <= 0) {
            return false;
        }
        List<Integer> offene = offene(artikel.angebrochen);
        if (offene.size() >= HOECHSTENS_OFFEN) {
            return false;
        }
        artikel.menge--;
        offene.add(artikel.packungsGroesse);
        artikel.angebrochen = alsText(offene);
        return true;
    }

    /**
     * Raeumt Widersprueche auf: Reste, die groesser sind als die Packung,
     * und offene Packungen ohne Verpackungseinheit. Wird vor dem Speichern
     * aufgerufen, damit nichts Unmoegliches in der Datenbank landet.
     */
    public static void geradeziehen(Artikel artikel) {
        if (artikel.menge < 0) {
            artikel.menge = 0;
        }
        if (!artikel.istVerpackung) {
            artikel.packungsGroesse = 0;
            artikel.angebrochen = null;
            return;
        }
        if (artikel.packungsGroesse < 1) {
            artikel.packungsGroesse = 1;
        }
        List<Integer> offene = offene(artikel.angebrochen);
        List<Integer> sauber = new ArrayList<>();
        for (int rest : offene) {
            sauber.add(Math.min(rest, artikel.packungsGroesse));
        }
        artikel.angebrochen = alsText(sauber);
    }
}
