package de.tagstock.server.dienst;

import de.tagstock.server.modell.Artikel;

import java.util.Arrays;
import java.util.List;

/**
 * Fuehrt zwei Fassungen eines Artikels zusammen, die unabhaengig voneinander
 * aus demselben Stand entstanden sind - etwa zwei Handys, die ohne Netz beide
 * etwas entnommen haben.
 *
 * <p>Die App schickt mit ihrer Fassung den Stand, auf dem sie aufgesetzt hat
 * (die Basis). Daran laesst sich je Feld ablesen, wer was geaendert hat:</p>
 * <ul>
 *   <li>nur eine Seite - deren Wert gilt,</li>
 *   <li>beide auf denselben Wert - der gilt,</li>
 *   <li>beide verschieden - die spaetere Aenderung gewinnt,</li>
 *   <li>ausser bei der Menge: Dort zaehlen beide. Von 10 Patchkabeln nimmt
 *       das eine Handy 2 und das andere 1 - danach sind es 7.</li>
 * </ul>
 *
 * <p>Was zusammengehoert, wird als Ganzes entschieden, damit keine
 * Mischung entsteht, die keiner gewollt hat: Status mit Ausleiher und
 * Rueckgabedatum, Behaelter mit seiner Art, Menge mit Packung und Resten.</p>
 */
public final class Zusammenfuehrung {

    private Zusammenfuehrung() {
    }

    /**
     * @param basis  der Stand, den das Geraet zuletzt vom Server hatte
     * @param geraet was das Geraet daraus gemacht hat
     * @param server was inzwischen auf dem Server steht
     * @return eine neue Fassung; die drei Eingaben bleiben unberuehrt
     */
    public static Artikel zusammenfuehren(Artikel basis, Artikel geraet, Artikel server) {
        Artikel ergebnis = kopie(server);
        boolean geraetSpaeter = geraet.geaendertAm >= server.geaendertAm;

        if (vomGeraet(feld(basis.name), feld(geraet.name), feld(server.name), geraetSpaeter)) {
            ergebnis.name = geraet.name;
        }
        if (vomGeraet(feld(basis.beschreibung), feld(geraet.beschreibung),
                feld(server.beschreibung), geraetSpaeter)) {
            ergebnis.beschreibung = geraet.beschreibung;
        }
        if (vomGeraet(feld(basis.kategorie), feld(geraet.kategorie), feld(server.kategorie),
                geraetSpaeter)) {
            ergebnis.kategorie = geraet.kategorie;
        }
        if (vomGeraet(feld(basis.standort), feld(geraet.standort), feld(server.standort),
                geraetSpaeter)) {
            ergebnis.standort = geraet.standort;
        }
        if (vomGeraet(feld(basis.lagerort), feld(geraet.lagerort), feld(server.lagerort),
                geraetSpaeter)) {
            ergebnis.lagerort = geraet.lagerort;
        }
        if (vomGeraet(feld(basis.bildUrl), feld(geraet.bildUrl), feld(server.bildUrl),
                geraetSpaeter)) {
            ergebnis.bildUrl = geraet.bildUrl;
        }
        if (vomGeraet(feld(basis.rfidUid), feld(geraet.rfidUid), feld(server.rfidUid),
                geraetSpaeter)) {
            ergebnis.rfidUid = geraet.rfidUid;
        }
        if (vomGeraet(feld(basis.scanWarnung), feld(geraet.scanWarnung),
                feld(server.scanWarnung), geraetSpaeter)) {
            ergebnis.scanWarnung = geraet.scanWarnung;
        }
        if (vomGeraet(feld(basis.behaelterKennung), feld(geraet.behaelterKennung),
                feld(server.behaelterKennung), geraetSpaeter)) {
            ergebnis.behaelterKennung = geraet.behaelterKennung;
        }
        if (vomGeraet(verleih(basis), verleih(geraet), verleih(server), geraetSpaeter)) {
            ergebnis.status = geraet.status;
            ergebnis.verliehenAn = geraet.verliehenAn;
            ergebnis.rueckgabeDatum = geraet.rueckgabeDatum;
        }
        if (vomGeraet(behaelter(basis), behaelter(geraet), behaelter(server), geraetSpaeter)) {
            ergebnis.istBehaelter = geraet.istBehaelter;
            ergebnis.behaelterArt = geraet.behaelterArt;
        }
        // Gescannt ist gescannt - der juengste Scan zaehlt, egal von wem.
        ergebnis.zuletztGescannt = spaeter(geraet.zuletztGescannt, server.zuletztGescannt);

        mengeZusammenfuehren(basis, geraet, server, ergebnis, geraetSpaeter);
        return ergebnis;
    }

    /**
     * Hat nur eine Seite die Menge angefasst, gilt deren Stand. Haben es beide,
     * kommt die Aenderung des Geraets obendrauf: Was es entnommen hat, wird
     * vom Serverstand entnommen, was dazukam, kommt dazu. Wurde dabei die
     * Packung selbst umgestellt, laesst sich nichts verrechnen - dann gewinnt
     * die spaetere Fassung.
     */
    private static void mengeZusammenfuehren(Artikel basis, Artikel geraet, Artikel server,
                                             Artikel ergebnis, boolean geraetSpaeter) {
        Object[] b = menge(basis);
        Object[] g = menge(geraet);
        Object[] s = menge(server);
        if (Arrays.equals(g, b) || Arrays.equals(g, s)) {
            return;
        }
        if (Arrays.equals(s, b) || !gleichVerpackt(basis, geraet, server)) {
            if (Arrays.equals(s, b) || geraetSpaeter) {
                mengeUebernehmen(geraet, ergebnis);
            }
            return;
        }
        int unterschied = Packungen.gesamt(geraet) - Packungen.gesamt(basis);
        if (unterschied < 0) {
            Packungen.entnehmen(ergebnis, -unterschied);
        } else if (unterschied > 0) {
            dazulegen(ergebnis, unterschied);
        }
        // Der Status folgt der Menge, die jetzt herauskommt - nicht der einer
        // Seite allein. Sonst stuende "nicht vorhanden" bei drei Stueck im Regal.
        ergebnis.status = Packungen.statusFuer(ergebnis.status, Packungen.gesamt(ergebnis));
    }

    /** Stueck dazu: so viele volle Packungen wie moeglich, der Rest als offene. */
    private static void dazulegen(Artikel artikel, int stueck) {
        if (!artikel.istVerpackung || artikel.packungsGroesse <= 0) {
            artikel.menge = Math.max(0, artikel.menge) + stueck;
            return;
        }
        Packungen.zugang(artikel, stueck / artikel.packungsGroesse);
        int rest = stueck % artikel.packungsGroesse;
        if (rest > 0) {
            List<Integer> offene = Packungen.offene(artikel.angebrochen);
            offene.add(rest);
            artikel.angebrochen = Packungen.alsText(offene);
        }
    }

    private static boolean gleichVerpackt(Artikel a, Artikel b, Artikel c) {
        return a.istVerpackung == b.istVerpackung && b.istVerpackung == c.istVerpackung
                && (!a.istVerpackung || (a.packungsGroesse == b.packungsGroesse
                && b.packungsGroesse == c.packungsGroesse));
    }

    private static void mengeUebernehmen(Artikel von, Artikel nach) {
        nach.menge = von.menge;
        nach.istVerpackung = von.istVerpackung;
        nach.packungsGroesse = von.packungsGroesse;
        nach.angebrochen = von.angebrochen;
    }

    /** true, wenn der Wert des Geraets gilt; false, wenn der des Servers bleibt. */
    private static boolean vomGeraet(Object[] basis, Object[] geraet, Object[] server,
                                     boolean geraetSpaeter) {
        if (Arrays.equals(geraet, basis) || Arrays.equals(geraet, server)) {
            return false;
        }
        return Arrays.equals(server, basis) || geraetSpaeter;
    }

    private static Object[] feld(String wert) {
        return new Object[]{sauber(wert)};
    }

    private static Object[] verleih(Artikel a) {
        return new Object[]{sauber(a.status), sauber(a.verliehenAn), a.rueckgabeDatum};
    }

    private static Object[] behaelter(Artikel a) {
        return new Object[]{a.istBehaelter, a.istBehaelter ? sauber(a.behaelterArt) : null};
    }

    private static Object[] menge(Artikel a) {
        boolean verpackt = a.istVerpackung && a.packungsGroesse > 0;
        return new Object[]{verpackt, verpackt ? a.packungsGroesse : 0, Math.max(0, a.menge),
                verpackt ? Packungen.alsText(Packungen.offene(a.angebrochen)) : null};
    }

    /** Leer und nicht gesetzt sind dasselbe; Leerzeichen am Rand zaehlen nicht. */
    private static String sauber(String wert) {
        return wert == null || wert.trim().isEmpty() ? null : wert.trim();
    }

    private static Long spaeter(Long a, Long b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : Math.max(a, b);
    }

    private static Artikel kopie(Artikel von) {
        Artikel a = new Artikel();
        a.id = von.id;
        a.teamId = von.teamId;
        a.rfidUid = von.rfidUid;
        a.name = von.name;
        a.beschreibung = von.beschreibung;
        a.kategorie = von.kategorie;
        a.standort = von.standort;
        a.lagerort = von.lagerort;
        a.bildUrl = von.bildUrl;
        a.status = von.status;
        a.verliehenAn = von.verliehenAn;
        a.rueckgabeDatum = von.rueckgabeDatum;
        a.zuletztGescannt = von.zuletztGescannt;
        a.scanWarnung = von.scanWarnung;
        a.menge = von.menge;
        a.istVerpackung = von.istVerpackung;
        a.packungsGroesse = von.packungsGroesse;
        a.angebrochen = von.angebrochen;
        a.istBehaelter = von.istBehaelter;
        a.behaelterArt = von.behaelterArt;
        a.behaelterKennung = von.behaelterKennung;
        a.erstelltAm = von.erstelltAm;
        a.geaendertAm = von.geaendertAm;
        a.geloescht = von.geloescht;
        a.lokaleId = von.lokaleId;
        return a;
    }
}
