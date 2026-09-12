package de.tagstock.server.dienst;

import de.tagstock.server.modell.Artikel;

import java.util.ArrayList;
import java.util.List;

/**
 * Rechnen mit Mengen und Verpackungseinheiten - dieselben Regeln wie in der
 * App, damit beide Seiten unabhaengig voneinander zum gleichen Ergebnis
 * kommen.
 *
 * <p>Ohne Verpackungseinheit ist {@code menge} die Stueckzahl. Mit einer
 * zaehlt sie die <b>vollen</b> Packungen; was in angebrochenen liegt, steht
 * einzeln daneben - im Regal wird durchaus versehentlich eine zweite
 * aufgerissen.</p>
 */
public final class Packungen {

    public static final int HOECHSTENS_OFFEN = 20;

    private Packungen() {
    }

    public static List<Integer> offene(String gespeichert) {
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
                // Eine unlesbare Zahl darf nicht den ganzen Artikel kippen.
            }
            if (reste.size() >= HOECHSTENS_OFFEN) {
                break;
            }
        }
        return reste;
    }

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

    /** Stueck insgesamt - volle Packungen und Reste zusammen. */
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

    /** Zuerst aus der angebrochenen Packung; ist die leer, wird angebrochen. */
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

    /** Mit Verpackungseinheit ganze Packungen, sonst Stueck. */
    public static void zugang(Artikel artikel, int anzahl) {
        if (anzahl > 0) {
            artikel.menge = Math.max(0, artikel.menge) + anzahl;
        }
    }

    /** Eine weitere Packung anbrechen, ohne etwas zu entnehmen. */
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

    /** Raeumt Widersprueche weg, bevor etwas in der Datenbank landet. */
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
        List<Integer> sauber = new ArrayList<>();
        for (int rest : offene(artikel.angebrochen)) {
            sauber.add(Math.min(rest, artikel.packungsGroesse));
        }
        artikel.angebrochen = alsText(sauber);
    }
}
