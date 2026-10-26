package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.tagstock.server.dienst.Packungen;
import de.tagstock.server.dienst.Zusammenfuehrung;
import de.tagstock.server.modell.Artikel;

import org.junit.jupiter.api.Test;

/**
 * Zwei Handys aendern ohne Netz denselben Artikel. Basis ist jeweils der
 * gemeinsame Stand; das Geraet ist das, das gerade abgleicht, der Server hat
 * die Fassung des anderen schon.
 */
class ZusammenfuehrungTest {

    private Artikel kabel(int stueck) {
        Artikel a = new Artikel();
        a.id = "k1";
        a.name = "Patchkabel 2 m";
        a.standort = "Werkstatt";
        a.menge = stueck;
        a.geaendertAm = 1000;
        return a;
    }

    private Artikel shelly(int volle, String offen) {
        Artikel a = new Artikel();
        a.id = "s1";
        a.name = "Shelly 1 Gen4";
        a.istVerpackung = true;
        a.packungsGroesse = 4;
        a.menge = volle;
        a.angebrochen = offen;
        a.geaendertAm = 1000;
        return a;
    }

    @Test
    void beideEntnahmenZaehlen() {
        Artikel basis = kabel(10);
        Artikel server = kabel(9);      // das andere Handy hat 1 genommen
        server.geaendertAm = 2000;
        Artikel geraet = kabel(8);      // dieses 2
        geraet.geaendertAm = 1500;

        assertEquals(7, Zusammenfuehrung.zusammenfuehren(basis, geraet, server).menge);
    }

    @Test
    void zugangUndEntnahmeVerrechnen() {
        Artikel basis = kabel(10);
        Artikel server = kabel(15);     // 5 dazugekommen
        Artikel geraet = kabel(7);      // 3 genommen

        assertEquals(12, Zusammenfuehrung.zusammenfuehren(basis, geraet, server).menge);
    }

    @Test
    void mehrAlsDaIstWirdNichtNegativ() {
        Artikel basis = kabel(3);
        Artikel server = kabel(0);
        Artikel geraet = kabel(1);

        Artikel ergebnis = Zusammenfuehrung.zusammenfuehren(basis, geraet, server);
        assertEquals(0, ergebnis.menge);
    }

    @Test
    void verschiedeneFelderBleibenBeide() {
        Artikel basis = kabel(10);
        Artikel server = kabel(10);
        server.name = "Patchkabel 2 m blau";
        server.geaendertAm = 3000;
        Artikel geraet = kabel(10);
        geraet.standort = "Keller";
        geraet.geaendertAm = 2000;

        Artikel ergebnis = Zusammenfuehrung.zusammenfuehren(basis, geraet, server);
        assertEquals("Patchkabel 2 m blau", ergebnis.name);
        assertEquals("Keller", ergebnis.standort);
    }

    @Test
    void gleichesFeldSpaetereAenderungGewinnt() {
        Artikel basis = kabel(10);
        Artikel server = kabel(10);
        server.standort = "Garage";
        server.geaendertAm = 2000;
        Artikel geraet = kabel(10);
        geraet.standort = "Keller";
        geraet.geaendertAm = 3000;

        assertEquals("Keller", Zusammenfuehrung.zusammenfuehren(basis, geraet, server).standort);

        geraet.geaendertAm = 1500;
        assertEquals("Garage", Zusammenfuehrung.zusammenfuehren(basis, geraet, server).standort);
    }

    @Test
    void nurDasGeraetHatGeaendertDannGiltEsGanz() {
        Artikel basis = kabel(10);
        Artikel server = kabel(10);
        Artikel geraet = kabel(4);
        geraet.beschreibung = "Cat 6";
        geraet.geaendertAm = 500;   // auch mit falsch gehender Uhr

        Artikel ergebnis = Zusammenfuehrung.zusammenfuehren(basis, geraet, server);
        assertEquals(4, ergebnis.menge);
        assertEquals("Cat 6", ergebnis.beschreibung);
    }

    @Test
    void nichtsGeaendertLaesstDenServerstand() {
        Artikel basis = kabel(10);
        Artikel server = kabel(6);
        server.name = "Neu";
        Artikel geraet = kabel(10);
        geraet.geaendertAm = 9000;  // spaeter, aber ohne eigene Aenderung

        Artikel ergebnis = Zusammenfuehrung.zusammenfuehren(basis, geraet, server);
        assertEquals(6, ergebnis.menge);
        assertEquals("Neu", ergebnis.name);
    }

    @Test
    void packungenWerdenStueckweiseVerrechnet() {
        // 5 volle und offene mit 2 und 3: 25 Stueck.
        Artikel basis = shelly(5, "2,3");
        Artikel server = shelly(5, "1,3");  // anderes Handy: 1 genommen
        Artikel geraet = shelly(5, "3");    // dieses: 2 genommen

        Artikel ergebnis = Zusammenfuehrung.zusammenfuehren(basis, geraet, server);
        assertEquals(22, Packungen.gesamt(ergebnis));
        // Aus der angebrochenen Packung genommen, nicht eine neue aufgerissen.
        assertEquals(5, ergebnis.menge);
        assertEquals("2", ergebnis.angebrochen);
    }

    @Test
    void zugangInPackungenAufBeidenSeiten() {
        Artikel basis = shelly(2, null);
        Artikel server = shelly(3, null);   // eine Packung gekauft
        Artikel geraet = shelly(4, null);   // hier zwei

        assertEquals(5, Zusammenfuehrung.zusammenfuehren(basis, geraet, server).menge);
    }

    @Test
    void umgestellteVerpackungLaesstSichNichtVerrechnen() {
        Artikel basis = kabel(10);
        Artikel server = kabel(9);
        server.geaendertAm = 2000;
        Artikel geraet = shelly(3, null);   // hier auf 4er-Packungen umgestellt
        geraet.geaendertAm = 3000;

        Artikel ergebnis = Zusammenfuehrung.zusammenfuehren(basis, geraet, server);
        assertEquals(3, ergebnis.menge);
        assertEquals(4, ergebnis.packungsGroesse);
    }

    @Test
    void statusFolgtDerZusammengefuehrtenMenge() {
        // Dieses Handy nimmt die letzten 2 - bei ihm steht danach "nicht
        // vorhanden". Das andere hat inzwischen 3 nachgelegt.
        Artikel basis = kabel(2);
        Artikel server = kabel(5);
        server.geaendertAm = 2000;
        Artikel geraet = kabel(0);
        geraet.status = "nicht vorhanden";
        geraet.geaendertAm = 3000;

        Artikel ergebnis = Zusammenfuehrung.zusammenfuehren(basis, geraet, server);
        assertEquals(3, ergebnis.menge);
        assertEquals("vorhanden", ergebnis.status);
    }

    @Test
    void verleihBleibtBeisammen() {
        Artikel basis = kabel(1);
        Artikel server = kabel(1);
        server.status = "verliehen";
        server.verliehenAn = "Anna";
        server.geaendertAm = 3000;
        Artikel geraet = kabel(1);
        geraet.status = "ausgelagert";
        geraet.geaendertAm = 2000;

        Artikel ergebnis = Zusammenfuehrung.zusammenfuehren(basis, geraet, server);
        assertEquals("verliehen", ergebnis.status);
        assertEquals("Anna", ergebnis.verliehenAn);
    }

    @Test
    void leerUndNichtGesetztSindGleich() {
        Artikel basis = kabel(10);
        basis.beschreibung = "";
        Artikel server = kabel(10);
        server.beschreibung = "Cat 6";
        Artikel geraet = kabel(10);
        geraet.beschreibung = null;
        geraet.geaendertAm = 9000;

        assertEquals("Cat 6", Zusammenfuehrung.zusammenfuehren(basis, geraet, server)
                .beschreibung);
    }

    @Test
    void juengsterScanZaehlt() {
        Artikel basis = kabel(1);
        Artikel server = kabel(1);
        server.zuletztGescannt = 5000L;
        Artikel geraet = kabel(1);
        geraet.zuletztGescannt = 4000L;

        assertEquals(5000L, Zusammenfuehrung.zusammenfuehren(basis, geraet, server)
                .zuletztGescannt);
        assertNull(Zusammenfuehrung.zusammenfuehren(basis, kabel(1), kabel(1))
                .zuletztGescannt);
    }
}
