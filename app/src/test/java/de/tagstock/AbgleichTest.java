package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.tagstock.data.Abgleich;
import de.tagstock.data.AppDatabase;
import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelDao;
import de.tagstock.util.Einstellungen;

/**
 * Der Abgleich gegen einen kleinen Server auf dem eigenen Rechner: Was auf
 * dem Geraet geloescht wurde, muss dort ankommen, und was waehrend der
 * Uebertragung geaendert wird, darf der Serverstand nicht ueberschreiben.
 */
@RunWith(RobolectricTestRunner.class)
public class AbgleichTest {

    private Context context;
    private HttpServer server;
    private ArtikelDao artikelDao;
    /** Room mag den Hauptthread nicht - alles mit der Datenbank laeuft hier. */
    private final ExecutorService hintergrund = Executors.newSingleThreadExecutor();

    private final List<String> anfragen = Collections.synchronizedList(new ArrayList<>());
    private volatile String antwort = "{}";
    private volatile Runnable waehrendDerUebertragung = () -> {
    };
    private boolean gestoppt;

    @Before
    public void vorbereiten() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", austausch -> {
            String was = austausch.getRequestMethod() + " " + austausch.getRequestURI().getPath();
            anfragen.add(was);
            austausch.getRequestBody().readAllBytes();
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
            austausch.getResponseHeaders().add("Content-Type", "application/json");
            austausch.sendResponseHeaders(status, daten.length);
            try (OutputStream aus = austausch.getResponseBody()) {
                aus.write(daten);
            }
        });
        server.start();

        Einstellungen.setzeServer(context, "http://127.0.0.1:" + server.getAddress().getPort(),
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
    public void aufraeumen() throws Exception {
        serverAnhalten();
        hintergrund.shutdownNow();
        // Sonst plant die App beim naechsten Test den Abgleich im Hintergrund ein.
        Einstellungen.abmelden(context);
        // Jeder Test bekommt eine eigene App - und damit eine eigene Datenbank.
        Field instanz = AppDatabase.class.getDeclaredField("instance");
        instanz.setAccessible(true);
        AppDatabase db = (AppDatabase) instanz.get(null);
        if (db != null) {
            db.close();
        }
        instanz.set(null, null);
    }

    private void serverAnhalten() {
        if (!gestoppt) {
            gestoppt = true;
            server.stop(0);
        }
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
        Einstellungen.loeschungVormerken(context, "srv-1");
        serverAnhalten();

        Abgleich.Ergebnis ergebnis = imHintergrund(() -> Abgleich.jetzt(context));

        assertFalse(ergebnis.erfolgreich());
        assertTrue(Einstellungen.loeschungen(context).contains("srv-1"));
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
