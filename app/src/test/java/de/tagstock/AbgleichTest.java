package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.tagstock.data.Abgleich;
import de.tagstock.data.AppDatabase;
import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelDao;
import de.tagstock.data.Protokoll;
import de.tagstock.util.Einstellungen;

/**
 * Der Abgleich gegen einen kleinen Server auf dem eigenen Rechner: Was auf
 * dem Geraet geloescht wurde, muss dort ankommen, und was waehrend der
 * Uebertragung geaendert wird, darf der Serverstand nicht ueberschreiben.
 */
@RunWith(RobolectricTestRunner.class)
public class AbgleichTest {

    private Context context;
    /** Schlichter Socket statt eines fertigen Servers - der Test kennt nur Android-Klassen. */
    private ServerSocket server;
    private ArtikelDao artikelDao;
    /** Room mag den Hauptthread nicht - alles mit der Datenbank laeuft hier. */
    private final ExecutorService hintergrund = Executors.newSingleThreadExecutor();

    private final List<String> anfragen = Collections.synchronizedList(new ArrayList<>());
    /** Was die App zuletzt zum Abgleich geschickt hat - Adresse und Inhalt. */
    private volatile String letzteAdresse = "";
    private volatile String letzterInhalt = "";
    private volatile String antwort = "{}";
    private volatile Runnable waehrendDerUebertragung = () -> {
    };

    @Before
    public void vorbereiten() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        Thread annahme = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket verbindung = server.accept()) {
                    beantworten(verbindung);
                } catch (IOException geschlossen) {
                    // Der Test ist fertig oder hat den Server absichtlich angehalten.
                }
            }
        });
        annahme.setDaemon(true);
        annahme.start();

        Einstellungen.setzeServer(context, "http://127.0.0.1:" + server.getLocalPort(),
                "geheim");
        Einstellungen.setzeTeam(context, "team1", "Werkstatt", "admin");
        artikelDao = AppDatabase.getInstance(context).artikelDao();
        imHintergrund(() -> {
            AppDatabase.getInstance(context).protokollDao().alleLoeschen();
            artikelDao.alleLoeschen();
            return null;
        });
    }

    @After
    public void aufraeumen() throws IOException {
        server.close();
        hintergrund.shutdownNow();
        // Sonst plant die App beim naechsten Test den Abgleich im Hintergrund ein.
        Einstellungen.abmelden(context);
        // Jeder Test bekommt eine eigene App - und damit eine eigene Datenbank.
        AppDatabase.getInstance(context).close();
        AppDatabase.resetInstance();
    }

    /** Eine Anfrage lesen und so antworten, wie es der echte Server taete. */
    private void beantworten(Socket verbindung) throws IOException {
        InputStream ein = new BufferedInputStream(verbindung.getInputStream());
        String[] anfrage = zeile(ein).split(" ");
        int laenge = 0;
        for (String kopf = zeile(ein); !kopf.isEmpty(); kopf = zeile(ein)) {
            if (kopf.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                laenge = Integer.parseInt(kopf.substring("content-length:".length()).trim());
            }
        }
        java.io.ByteArrayOutputStream inhalt = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < laenge; i++) {
            int zeichen = ein.read();
            if (zeichen == -1) {
                break;
            }
            inhalt.write(zeichen);
        }
        String pfad = anfrage.length > 1 ? anfrage[1].split("\\?")[0] : "";
        String was = anfrage[0] + " " + pfad;
        anfragen.add(was);
        if (was.endsWith("/sync")) {
            letzteAdresse = anfrage[1];
            letzterInhalt = new String(inhalt.toByteArray(), StandardCharsets.UTF_8);
        }

        int status = 200;
        String text = "{}";
        if (was.startsWith("DELETE")) {
            if (was.endsWith("/schon-weg")) {
                status = 404;
                text = "{\"fehler\":\"Artikel nicht gefunden\"}";
            } else {
                text = "{\"geloescht\":true}";
            }
        } else if (was.endsWith("/sync")) {
            waehrendDerUebertragung.run();
            text = antwort;
        }
        byte[] daten = text.getBytes(StandardCharsets.UTF_8);
        OutputStream aus = verbindung.getOutputStream();
        aus.write(("HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found") + "\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + daten.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        aus.write(daten);
        aus.flush();
    }

    private static String zeile(InputStream ein) throws IOException {
        StringBuilder text = new StringBuilder();
        int zeichen;
        while ((zeichen = ein.read()) != -1 && zeichen != '\n') {
            if (zeichen != '\r') {
                text.append((char) zeichen);
            }
        }
        return text.toString();
    }

    @Test
    public void loeschungenGehenZumServerUndSindDannErledigt() throws Exception {
        Einstellungen.loeschungVormerken(context, "srv-1");
        Einstellungen.loeschungVormerken(context, "schon-weg");
        antwort = "{\"stand\":5,\"artikel\":[],\"kategorien\":[],\"zuordnungen\":[]}";

        Abgleich.Ergebnis ergebnis = imHintergrund(() -> Abgleich.jetzt(context));

        assertTrue(ergebnis.fehler, ergebnis.erfolgreich());
        assertTrue(anfragen.contains("DELETE /api/v1/teams/team1/artikel/srv-1"));
        assertTrue(anfragen.contains("DELETE /api/v1/teams/team1/artikel/schon-weg"));
        // Den einen gab es auf dem Server schon nicht mehr - auch gut.
        assertTrue(Einstellungen.loeschungen(context).isEmpty());
    }

    @Test
    public void ohneServerBleibtDieLoeschungVorgemerkt() throws Exception {
        // Ein Anschluss, an dem nie jemand gelauscht hat. Den laufenden
        // Probeserver zu schliessen reicht nicht: Wartet sein Faden gerade auf
        // eine Verbindung, nimmt der JDK gelegentlich noch eine an.
        int tot;
        try (ServerSocket kurz = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            tot = kurz.getLocalPort();
        }
        Einstellungen.setzeServer(context, "http://127.0.0.1:" + tot, "geheim");
        Einstellungen.loeschungVormerken(context, "srv-1");

        Abgleich.Ergebnis ergebnis = imHintergrund(() -> Abgleich.jetzt(context));

        assertFalse("Anfragen: " + anfragen, ergebnis.erfolgreich());
        // Kein Fehler, den Wiederholen behebt - erst ein anderes Netz hilft.
        assertTrue(ergebnis.unerreichbar);
        assertTrue("Fehler: " + ergebnis.fehler,
                Einstellungen.loeschungen(context).contains("srv-1"));
    }

    @Test
    public void vorgemerkteLoeschungKommtNichtMitDemServerstandZurueck() throws Exception {
        // Waehrend der Uebertragung loescht jemand auf dem Geraet einen Artikel,
        // den der Server im selben Moment noch mitschickt.
        waehrendDerUebertragung = () -> Einstellungen.loeschungVormerken(context, "srv-7");
        antwort = "{\"stand\":5,\"kategorien\":[],\"zuordnungen\":[],\"artikel\":["
                + "{\"id\":\"srv-7\",\"name\":\"Alte Lampe\",\"status\":\"vorhanden\"}]}";

        Abgleich.Ergebnis ergebnis = imHintergrund(() -> Abgleich.jetzt(context));

        assertTrue(ergebnis.fehler, ergebnis.erfolgreich());
        assertNull(imHintergrund(() -> artikelDao.nachServerId("srv-7")));
    }

    @Test
    public void aenderungWaehrendDerUebertragungBleibtOffen() throws Exception {
        long kabel = neuerArtikel("Patchkabel", 5);
        long hammer = neuerArtikel("Hammer", 1);
        // Waehrend die Daten unterwegs sind, wird ein Kabel entnommen.
        waehrendDerUebertragung = () -> {
            Artikel artikel = artikelDao.nachId(kabel);
            artikel.menge = 4;
            artikel.geaendertAm = artikel.geaendertAm + 1000;
            artikel.offen = true;
            artikelDao.update(artikel);
        };
        antwort = "{\"stand\":5,\"kategorien\":[],"
                + "\"zuordnungen\":["
                + "{\"lokaleId\":" + kabel + ",\"serverId\":\"srv-k\",\"angenommen\":true},"
                + "{\"lokaleId\":" + hammer + ",\"serverId\":\"srv-h\",\"angenommen\":true}],"
                + "\"artikel\":["
                + "{\"id\":\"srv-k\",\"name\":\"Patchkabel\",\"menge\":5,\"status\":\"vorhanden\"},"
                + "{\"id\":\"srv-h\",\"name\":\"Hammer\",\"menge\":1,\"status\":\"vorhanden\"}]}";

        Abgleich.Ergebnis ergebnis = imHintergrund(() -> Abgleich.jetzt(context));
        assertTrue(ergebnis.fehler, ergebnis.erfolgreich());

        Artikel nachher = imHintergrund(() -> artikelDao.nachId(kabel));
        assertEquals("srv-k", nachher.serverId);
        // Die Entnahme ist nicht verloren und geht beim naechsten Mal hoch.
        assertEquals(4, nachher.menge);
        assertTrue(nachher.offen);

        Artikel unberuehrt = imHintergrund(() -> artikelDao.nachId(hammer));
        assertEquals("srv-h", unberuehrt.serverId);
        assertFalse(unberuehrt.offen);
    }

    @Test
    public void aufDemServerGeloeschtesVerschwindetAuchHier() throws Exception {
        long stuhl = neuerArtikel("Kaputter Stuhl", 1);
        antwort = "{\"stand\":5,\"kategorien\":[],\"artikel\":[],\"zuordnungen\":["
                + "{\"lokaleId\":" + stuhl + ",\"serverId\":\"srv-s\",\"angenommen\":false,"
                + "\"geloescht\":true,\"grund\":\"Artikel wurde inzwischen gelöscht\"}]}";

        Abgleich.Ergebnis ergebnis = imHintergrund(() -> Abgleich.jetzt(context));

        assertTrue(ergebnis.fehler, ergebnis.erfolgreich());
        assertEquals(1, ergebnis.geloescht);
        assertNull(imHintergrund(() -> artikelDao.nachId(stuhl)));
    }

    @Test
    public void serverstandWirdGemerktUndGehtBeimNaechstenMalMit() throws Exception {
        long kabel = neuerArtikel("Patchkabel", 10);
        antwort = "{\"stand\":5,\"kategorien\":[],"
                + "\"zuordnungen\":[{\"lokaleId\":" + kabel
                + ",\"serverId\":\"srv-k\",\"angenommen\":true}],"
                + "\"artikel\":[{\"id\":\"srv-k\",\"name\":\"Patchkabel\",\"menge\":10,"
                + "\"status\":\"vorhanden\",\"geaendertAm\":5}]}";
        assertTrue(imHintergrund(() -> Abgleich.jetzt(context)).erfolgreich());
        assertNotNull(imHintergrund(() -> artikelDao.nachId(kabel)).basis);

        // Ohne Netz 2 entnommen - der Server soll sehen, wovon das ausging.
        imHintergrund(() -> {
            Artikel artikel = artikelDao.nachId(kabel);
            artikel.menge = 8;
            artikel.geaendertAm++;
            artikel.offen = true;
            artikelDao.update(artikel);
            return null;
        });
        antwort = "{\"stand\":6,\"kategorien\":[],\"zuordnungen\":[],\"artikel\":[]}";
        assertTrue(imHintergrund(() -> Abgleich.jetzt(context)).erfolgreich());

        JSONObject gesendet = new JSONObject(letzterInhalt).getJSONArray("artikel")
                .getJSONObject(0);
        assertEquals(8, gesendet.getInt("menge"));
        assertEquals(10, gesendet.getJSONObject("basis").getInt("menge"));
    }

    @Test
    public void protokollDerAnderenKommtAnUndEigenesNichtDoppelt() throws Exception {
        long leiter = imHintergrund(() -> {
            Artikel artikel = new Artikel();
            artikel.name = "Leiter";
            artikel.serverId = "srv-l";
            artikel.offen = false;
            return artikelDao.insert(artikel);
        });
        imHintergrund(() -> {
            Protokoll eigener = new Protokoll();
            eigener.artikelId = leiter;
            eigener.artikelName = "Leiter";
            eigener.aktion = "Entnommen";
            eigener.zeitpunkt = 1500;
            return AppDatabase.getInstance(context).protokollDao().insert(eigener);
        });
        antwort = "{\"stand\":5,\"kategorien\":[],\"zuordnungen\":[],\"artikel\":[],"
                + "\"protokoll\":["
                // der eigene, vom Server zurueck
                + "{\"id\":\"p1\",\"artikelId\":\"srv-l\",\"artikelName\":\"Leiter\","
                + "\"aktion\":\"Entnommen\",\"zeitpunkt\":1500},"
                // einer vom anderen Handy
                + "{\"id\":\"p2\",\"artikelId\":\"srv-l\",\"artikelName\":\"Leiter\","
                + "\"aktion\":\"Zugang\",\"alterWert\":\"3\",\"neuerWert\":\"5\","
                + "\"nutzer\":\"Anna\",\"zeitpunkt\":1600},"
                // einer zu einem Artikel, den es hier nicht gibt
                + "{\"id\":\"p3\",\"artikelId\":\"weg\",\"artikelName\":\"Alt\","
                + "\"aktion\":\"Zugang\",\"zeitpunkt\":1700}]}";

        assertTrue(imHintergrund(() -> Abgleich.jetzt(context)).erfolgreich());
        // Der eigene ging mit der Nummer des Artikels hoch.
        assertEquals(leiter, new JSONObject(letzterInhalt).getJSONArray("protokoll")
                .getJSONObject(0).getLong("lokaleArtikelId"));
        // Und kommt noch einmal - nichts darf doppelt werden.
        assertTrue(imHintergrund(() -> Abgleich.jetzt(context)).erfolgreich());

        List<Protokoll> verlauf = imHintergrund(() ->
                AppDatabase.getInstance(context).protokollDao().alle());
        assertEquals("Verlauf: " + verlauf.size(), 2, verlauf.size());
        boolean vonAnna = false;
        for (Protokoll eintrag : verlauf) {
            assertNotNull(eintrag.serverId);
            vonAnna |= "Anna".equals(eintrag.nutzer) && "5".equals(eintrag.neuerWert);
        }
        assertTrue(vonAnna);
    }

    @Test
    public void ersterAbgleichNachDemUpdateHoltAlles() throws Exception {
        Einstellungen.setzeLetztenSync(context, 1234L);
        antwort = "{\"stand\":5,\"kategorien\":[],\"zuordnungen\":[],\"artikel\":[]}";

        assertTrue(imHintergrund(() -> Abgleich.jetzt(context)).erfolgreich());
        assertTrue(letzteAdresse, letzteAdresse.endsWith("seit=0"));

        assertTrue(imHintergrund(() -> Abgleich.jetzt(context)).erfolgreich());
        assertTrue(letzteAdresse, letzteAdresse.endsWith("seit=5"));
    }

    // ----------------------------------------------------------- Hilfsmittel

    private long neuerArtikel(String name, int menge) throws Exception {
        return imHintergrund(() -> {
            Artikel artikel = new Artikel();
            artikel.name = name;
            artikel.menge = menge;
            artikel.offen = true;
            return artikelDao.insert(artikel);
        });
    }

    private <T> T imHintergrund(Callable<T> arbeit) throws Exception {
        return hintergrund.submit(arbeit).get(30, TimeUnit.SECONDS);
    }
}
