package de.tagstock.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import de.tagstock.util.Einstellungen;
import de.tagstock.util.Fotos;
import de.tagstock.util.ServerClient;
import de.tagstock.util.Serverbilder;

/**
 * Abgleich zwischen App und Server: eigene Aenderungen hochladen, fremde
 * uebernehmen - Artikel wie Protokoll. Zu jedem Artikel geht der Stand mit,
 * den die App zuletzt vom Server hatte; daran fuehrt der Server die
 * Aenderungen zweier Geraete zusammen, statt eine zu verlieren. Auch die
 * Artikelbilder gehen dabei auf den Server; auf dem Geraet bleibt nur ein
 * Zwischenspeicher.
 */
public final class Abgleich {

    /** Was der Abgleich bewegt hat. */
    public static class Ergebnis {
        public int hochgeladen;
        public int abgelehnt;
        public int uebernommen;
        public int geloescht;
        public int bilder;
        /** Warum der Server etwas nicht angenommen hat - fuer die Meldung. */
        public final List<String> gruende = new ArrayList<>();
        @Nullable
        public String fehler;
        public boolean abgemeldet;
        /** true, wenn gerade schon ein Abgleich lief - dann war das hier kein Fehler. */
        public boolean laeuftSchon;
        /**
         * true, wenn der Server gar nicht zu erreichen war - etwa unterwegs
         * ohne VPN. Dann hilft nur ein anderes Netz, kein Wiederholen.
         */
        public boolean unerreichbar;

        public boolean erfolgreich() {
            return fehler == null;
        }
    }

    /**
     * Eigener Thread: Der Abgleich wartet auf das Netz, und solange soll
     * niemand mit dem Entnehmen oder Speichern auf ihn warten muessen.
     */
    private static final ExecutorService ARBEITER = Executors.newSingleThreadExecutor();
    private static final Handler HAUPTTHREAD = new Handler(Looper.getMainLooper());

    private Abgleich() {
    }

    /**
     * Fuehrt einen vollstaendigen Abgleich aus; die Antwort kommt im
     * Hauptthread. Laeuft gerade einer im Hintergrund, wartet dieser - wer
     * von Hand abgleicht, will ein Ergebnis und keine Ausrede.
     */
    public static void ausfuehren(Context context, Repository.Callback<Ergebnis> callback) {
        ausfuehren(context, true, callback);
    }

    /**
     * Fuer den Takt, solange die App offen ist: Laeuft gerade einer, faellt
     * dieser aus - der naechste Takt kommt bestimmt.
     */
    public static void nebenbei(Context context) {
        ausfuehren(context, false, ergebnis -> {
        });
    }

    private static void ausfuehren(Context context, boolean warten,
                                   Repository.Callback<Ergebnis> callback) {
        Context app = context.getApplicationContext();
        Ergebnis vorab = vorpruefung(app);
        if (vorab != null) {
            callback.onResult(vorab);
            return;
        }
        ARBEITER.execute(() -> {
            Ergebnis ergebnis = abgleichen(app, warten);
            HAUPTTHREAD.post(() -> callback.onResult(ergebnis));
        });
    }

    /**
     * Derselbe Abgleich, nur blockierend - dafuer aus einem Hintergrund-Thread
     * aufzurufen. Nutzt die geplante Uebertragung, sobald wieder Netz da ist.
     */
    @WorkerThread
    public static Ergebnis jetzt(Context context) {
        // Im Hintergrund wartet er, bis ein laufender Abgleich fertig ist:
        // Der hat womoeglich nicht mehr mitgenommen, weswegen dieser startet.
        return abgleichen(context, true);
    }

    private static Ergebnis abgleichen(Context context, boolean warten) {
        Ergebnis vorab = vorpruefung(context);
        if (vorab != null) {
            return vorab;
        }
        try {
            return durchfuehren(context, AppDatabase.getInstance(context), warten);
        } catch (Exception fehler) {
            Ergebnis ergebnis = new Ergebnis();
            ergebnis.fehler = fehler.getMessage() == null
                    ? "Abgleich fehlgeschlagen" : fehler.getMessage();
            return ergebnis;
        }
    }

    /** Ergebnis mit Fehler, wenn gar kein Abgleich moeglich ist - sonst null. */
    @Nullable
    private static Ergebnis vorpruefung(Context context) {
        if (Einstellungen.serverUrl(context) == null || Einstellungen.token(context) == null
                || Einstellungen.teamId(context) == null) {
            Ergebnis ergebnis = new Ergebnis();
            ergebnis.fehler = "Kein Server eingerichtet";
            return ergebnis;
        }
        return null;
    }

    /** Zwei Abgleiche gleichzeitig braucht niemand - der zweite wartet oder faellt aus. */
    private static final ReentrantLock SPERRE = new ReentrantLock();

    /**
     * Der Abgleich laeuft neben der Oberflaeche her. Deshalb geht nichts ueber
     * das Netz, solange die Datenbank gesperrt ist, und was waehrend der
     * Uebertragung geaendert wurde, bleibt offen und wird nicht vom
     * Serverstand ueberschrieben - es geht beim naechsten Mal mit.
     */
    private static Ergebnis durchfuehren(Context context, AppDatabase db, boolean warten) {
        ArtikelDao artikelDao = db.artikelDao();
        KategorieDao kategorieDao = db.kategorieDao();
        ProtokollDao protokollDao = db.protokollDao();
        Ergebnis ergebnis = new Ergebnis();
        boolean gesperrt;
        try {
            gesperrt = warten ? SPERRE.tryLock(2, TimeUnit.MINUTES) : SPERRE.tryLock();
        } catch (InterruptedException unterbrochen) {
            Thread.currentThread().interrupt();
            gesperrt = false;
        }
        if (!gesperrt) {
            ergebnis.fehler = "Abgleich laeuft bereits";
            ergebnis.laeuftSchon = true;
            return ergebnis;
        }
        String url = Einstellungen.serverUrl(context);
        String token = Einstellungen.token(context);
        String teamId = Einstellungen.teamId(context);
        // Einmal alles: Das Protokoll der anderen Geraete kam frueher gar nicht
        // an - so ist auch das Aeltere danach auf jedem Geraet.
        boolean vollstaendig = Einstellungen.vollabgleichFaellig(context);
        long seit = vollstaendig ? 0L : Einstellungen.letzterSync(context);
        try {
            // Im try: Was hier schiefgeht, darf die Sperre nicht festhalten -
            // sonst liefe nie wieder ein Abgleich.
            ServerClient client = new ServerClient(url, token);
            // Geloeschtes zuerst - sonst kaeme es mit dem Serverstand zurueck.
            loeschungenMelden(context, client, teamId, ergebnis);

            // Dann die Bilder der bereits bekannten Artikel, damit die
            // Adresse gleich mit hochgeht.
            bilderHochladen(context, client, teamId, db, ergebnis);

            JSONArray offeneArtikel = new JSONArray();
            // Welcher Stand ging hoch? Wer danach noch geaendert wurde, bleibt offen.
            Map<Long, Long> gesendet = new HashMap<>();
            for (Artikel artikel : artikelDao.offene()) {
                offeneArtikel.put(alsJson(artikel));
                gesendet.put(artikel.id, artikel.geaendertAm);
            }

            JSONArray offeneEintraege = new JSONArray();
            List<Long> gesendeteEintraege = new ArrayList<>();
            Map<Long, String> kennungen = new HashMap<>();
            for (Protokoll eintrag : protokollDao.offene()) {
                if (!kennungen.containsKey(eintrag.artikelId)) {
                    Artikel dazu = artikelDao.nachId(eintrag.artikelId);
                    kennungen.put(eintrag.artikelId, dazu == null ? null : dazu.serverId);
                }
                offeneEintraege.put(alsJson(eintrag, kennungen.get(eintrag.artikelId)));
                gesendeteEintraege.add(eintrag.id);
            }

            JSONObject antwort = client.abgleichSenden(teamId, seit, offeneArtikel,
                    offeneEintraege);

            // Alles Eintragen am Stueck: Dazwischen kann niemand etwas aendern,
            // das gleich wieder ueberschrieben wuerde.
            db.runInTransaction(() -> {
                zuordnungenAnwenden(antwort.optJSONArray("zuordnungen"), gesendet, artikelDao,
                        ergebnis);
                serverstandUebernehmen(antwort.optJSONArray("artikel"), teamId,
                        Einstellungen.loeschungen(context), artikelDao, ergebnis);
                kategorienUebernehmen(antwort.optJSONArray("kategorien"), teamId, kategorieDao);
                if (!gesendeteEintraege.isEmpty()) {
                    protokollDao.alsGesendetMarkieren(gesendeteEintraege);
                }
                protokollUebernehmen(antwort.optJSONArray("protokoll"), artikelDao,
                        protokollDao);
            });

            // Neu angelegte Artikel haben jetzt eine Server-Kennung - ihre
            // Bilder koennen nachgereicht werden.
            bilderHochladen(context, client, teamId, db, ergebnis);

            long stand = antwort.optLong("stand", System.currentTimeMillis());
            Einstellungen.setzeLetztenSync(context, stand);
            if (vollstaendig) {
                Einstellungen.vollabgleichErledigt(context);
            }
        } catch (ServerClient.ServerFehler fehler) {
            ergebnis.fehler = fehler.getMessage();
            ergebnis.abgemeldet = fehler.istAbgemeldet();
            // 502 bis 504 kommen von einem Vorschaltrechner, wenn der Server
            // dahinter nicht antwortet.
            ergebnis.unerreichbar = fehler.status >= 502 && fehler.status <= 504;
        } catch (java.io.IOException fehler) {
            ergebnis.fehler = fehler.getMessage() == null
                    ? "Server nicht erreichbar" : fehler.getMessage();
            ergebnis.unerreichbar = true;
        } catch (Exception fehler) {
            ergebnis.fehler = fehler.getMessage() == null
                    ? "Abgleich fehlgeschlagen" : fehler.getMessage();
        } finally {
            SPERRE.unlock();
        }
        return ergebnis;
    }

    // ------------------------------------------------------------------ Senden

    private static JSONObject alsJson(Artikel artikel) throws JSONException {
        JSONObject o = new JSONObject();
        if (artikel.serverId != null) {
            o.put("id", artikel.serverId);
        }
        o.put("lokaleId", artikel.id);
        o.put("name", artikel.name);
        o.put("beschreibung", artikel.beschreibung);
        o.put("kategorie", artikel.kategorie);
        o.put("standort", artikel.standort);
        o.put("lagerort", artikel.lagerort);
        o.put("bildUrl", artikel.bildUrl);
        o.put("rfidUid", artikel.rfidUid);
        o.put("status", artikel.status.schluessel);
        o.put("verliehenAn", artikel.verliehenAn);
        o.put("rueckgabeDatum", artikel.rueckgabeDatum == null
                ? JSONObject.NULL : artikel.rueckgabeDatum);
        o.put("zuletztGescannt", artikel.zuletztGescannt == null
                ? JSONObject.NULL : artikel.zuletztGescannt);
        o.put("scanWarnung", artikel.scanWarnung.schluessel);
        o.put("menge", artikel.menge);
        o.put("istVerpackung", artikel.istVerpackung);
        o.put("packungsGroesse", artikel.packungsGroesse);
        o.put("angebrochen", artikel.angebrochen);
        o.put("istBehaelter", artikel.istBehaelter);
        o.put("behaelterArt", artikel.behaelterArt);
        o.put("behaelterKennung", artikel.behaelterKennung);
        o.put("erstelltAm", artikel.erstelltAm);
        o.put("geaendertAm", artikel.geaendertAm);
        if (artikel.basis != null) {
            try {
                o.put("basis", new JSONObject(artikel.basis));
            } catch (JSONException kaputt) {
                // Ohne Stand entscheidet der Server wie frueher nach der Zeit.
            }
        }
        return o;
    }

    private static JSONObject alsJson(Protokoll eintrag, @Nullable String artikelServerId)
            throws JSONException {
        JSONObject o = new JSONObject();
        o.put("artikelId", artikelServerId);
        // Fuer Artikel, die erst mit diesem Abgleich auf den Server kommen:
        // Der Server setzt dann selbst die richtige Kennung ein.
        o.put("lokaleArtikelId", eintrag.artikelId);
        o.put("artikelName", eintrag.artikelName);
        o.put("aktion", eintrag.aktion);
        o.put("alterWert", eintrag.alterWert);
        o.put("neuerWert", eintrag.neuerWert);
        o.put("nutzer", eintrag.nutzer);
        o.put("zeitpunkt", eintrag.zeitpunkt);
        return o;
    }

    // ------------------------------------------------------------- Loeschungen

    /**
     * Meldet dem Server, was auf dem Geraet geloescht wurde. Kennt er den
     * Artikel nicht mehr, ist das Ziel auch erreicht. Darf man nicht loeschen,
     * hilft Wiederholen nicht - dann bleibt es beim Hinweis.
     */
    private static void loeschungenMelden(Context context, ServerClient client, String teamId,
                                          Ergebnis ergebnis) throws java.io.IOException {
        for (String serverId : Einstellungen.loeschungen(context)) {
            try {
                client.artikelLoeschen(teamId, serverId);
                ergebnis.hochgeladen++;
            } catch (ServerClient.ServerFehler fehler) {
                if (fehler.status == 403) {
                    String grund = "Löschen ist nur mit Bearbeitungsrechten möglich";
                    if (!ergebnis.gruende.contains(grund)) {
                        ergebnis.gruende.add(grund);
                    }
                } else if (fehler.status != 404) {
                    throw fehler;
                }
            }
            Einstellungen.loeschungErledigt(context, serverId);
        }
    }

    // ------------------------------------------------------------------ Bilder

    /**
     * Schickt Aufnahmen, die noch nur auf dem Geraet liegen, an den Server und
     * merkt sich die Adresse. Danach ist die Datei im Zwischenspeicher; das
     * Original im App-Verzeichnis wird nicht mehr gebraucht.
     */
    private static void bilderHochladen(Context context, ServerClient client, String teamId,
                                        AppDatabase db, Ergebnis ergebnis) {
        ArtikelDao artikelDao = db.artikelDao();
        for (Artikel artikel : artikelDao.mitOffenemBild()) {
            byte[] daten = Serverbilder.zumHochladen(context, artikel.fotoPfad);
            if (daten == null) {
                // Die Datei ist weg - dann auch den Verweis loeschen.
                artikel.fotoPfad = null;
                artikelDao.update(artikel);
                continue;
            }
            try {
                JSONObject antwort = client.bildHochladen(teamId, artikel.serverId, daten,
                        "image/jpeg");
                String adresse = antwort.optString("bildUrl", "");
                if (adresse.isEmpty()) {
                    continue;
                }
                Serverbilder.uebernehmen(context, daten, adresse);
                Fotos.loeschen(context, artikel.fotoPfad);

                long stand = antwort.optLong("geaendertAm", 0L);
                boolean eingetragen = db.runInTransaction(() -> {
                    Artikel frisch = artikelDao.nachId(artikel.id);
                    if (frisch == null) {
                        return false;
                    }
                    frisch.bildUrl = adresse;
                    frisch.fotoPfad = null;
                    // Der Server hat den Artikel beim Ablegen angefasst; ohne
                    // diesen Stand wuerde er unsere eigenen Aenderungen gleich
                    // abweisen.
                    if (stand > frisch.geaendertAm) {
                        frisch.geaendertAm = stand;
                    }
                    artikelDao.update(frisch);
                    return true;
                });
                if (eingetragen) {
                    ergebnis.bilder++;
                }
            } catch (Exception fehler) {
                // Beim naechsten Abgleich noch einmal versuchen.
                return;
            }
        }
    }

    // ---------------------------------------------------------------- Annehmen

    /** Traegt die Server-Kennungen ein und merkt sich, was abgelehnt wurde. */
    private static void zuordnungenAnwenden(@Nullable JSONArray zuordnungen,
                                            Map<Long, Long> gesendet, ArtikelDao artikelDao,
                                            Ergebnis ergebnis) {
        if (zuordnungen == null) {
            return;
        }
        for (int i = 0; i < zuordnungen.length(); i++) {
            JSONObject zuordnung = zuordnungen.optJSONObject(i);
            if (zuordnung == null) {
                continue;
            }
            long lokaleId = zuordnung.optLong("lokaleId");
            String serverId = zuordnung.isNull("serverId")
                    ? null : zuordnung.optString("serverId");
            boolean angenommen = zuordnung.optBoolean("angenommen");

            Artikel artikel = artikelDao.nachId(lokaleId);
            if (artikel == null) {
                continue;
            }
            if (zuordnung.optBoolean("geloescht")) {
                // Auf dem Server schon geloescht - dann auch hier.
                artikelDao.delete(artikel);
                ergebnis.geloescht++;
                continue;
            }
            if (serverId != null && !serverId.isEmpty()) {
                artikel.serverId = serverId;
            }
            // In beiden Faellen ist der Artikel abgearbeitet: bei Ablehnung
            // gewinnt der Serverstand, der gleich mit uebernommen wird. Es sei
            // denn, er wurde waehrend der Uebertragung schon wieder geaendert -
            // dann muss diese Fassung beim naechsten Mal hoch.
            Long hochgeladen = gesendet.get(lokaleId);
            if (hochgeladen != null && hochgeladen == artikel.geaendertAm) {
                artikel.offen = false;
            }
            artikelDao.update(artikel);

            if (angenommen) {
                ergebnis.hochgeladen++;
            } else {
                ergebnis.abgelehnt++;
                String grund = zuordnung.isNull("grund") ? null : zuordnung.optString("grund");
                if (grund != null && !grund.isEmpty() && !ergebnis.gruende.contains(grund)) {
                    ergebnis.gruende.add(grund);
                }
            }
        }
    }

    /**
     * Uebernimmt die Artikel, die vom Server kommen. Was hier noch geloescht
     * gemeldet werden muss oder noch ungesendete Aenderungen hat, bleibt,
     * wie es ist.
     */
    private static void serverstandUebernehmen(@Nullable JSONArray artikelListe, String teamId,
                                               Set<String> geloeschtHier,
                                               ArtikelDao artikelDao, Ergebnis ergebnis) {
        if (artikelListe == null) {
            return;
        }
        for (int i = 0; i < artikelListe.length(); i++) {
            JSONObject o = artikelListe.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String serverId = o.optString("id", "");
            if (serverId.isEmpty() || geloeschtHier.contains(serverId)) {
                continue;
            }
            Artikel vorhanden = artikelDao.nachServerId(serverId);

            if (o.optBoolean("geloescht")) {
                if (vorhanden != null) {
                    artikelDao.delete(vorhanden);
                    ergebnis.geloescht++;
                }
                continue;
            }
            if (vorhanden != null && vorhanden.offen) {
                continue;
            }

            Artikel ziel = vorhanden == null ? new Artikel() : vorhanden;
            String kennung = leer(o.optString("rfidUid", null));
            if (kennung != null) {
                // Die Kennung gehoert auf dem Server einem anderen Artikel:
                // lokal freiraeumen, damit der eindeutige Index haelt.
                Artikel belegt = artikelDao.nachKennung(kennung);
                if (belegt != null && !serverId.equals(belegt.serverId)) {
                    belegt.rfidUid = null;
                    artikelDao.update(belegt);
                }
            }

            ziel.serverId = serverId;
            ziel.teamId = teamId;
            ziel.rfidUid = kennung;
            ziel.name = o.optString("name", "");
            ziel.beschreibung = leer(o.optString("beschreibung", null));
            ziel.kategorie = leer(o.optString("kategorie", null));
            ziel.standort = leer(o.optString("standort", null));
            ziel.lagerort = leer(o.optString("lagerort", null));
            ziel.bildUrl = leer(o.optString("bildUrl", null));
            ziel.status = ArtikelStatus.vonSchluessel(o.optString("status"));
            ziel.verliehenAn = leer(o.optString("verliehenAn", null));
            ziel.rueckgabeDatum = o.isNull("rueckgabeDatum") ? null : o.optLong("rueckgabeDatum");
            ziel.zuletztGescannt = o.isNull("zuletztGescannt") ? null : o.optLong("zuletztGescannt");
            ziel.scanWarnung = ScanWarnung.vonSchluessel(o.optString("scanWarnung"));
            ziel.menge = o.optInt("menge", 1);
            ziel.istVerpackung = o.optBoolean("istVerpackung");
            ziel.packungsGroesse = o.optInt("packungsGroesse");
            ziel.angebrochen = leer(o.optString("angebrochen", null));
            ziel.istBehaelter = o.optBoolean("istBehaelter");
            ziel.behaelterArt = leer(o.optString("behaelterArt", null));
            ziel.behaelterKennung = leer(o.optString("behaelterKennung", null));
            ziel.erstelltAm = o.optLong("erstelltAm", System.currentTimeMillis());
            ziel.geaendertAm = o.optLong("geaendertAm", ziel.erstelltAm);
            ziel.offen = false;
            // Ab hier baut jede Aenderung auf diesem Stand auf.
            ziel.basis = o.toString();

            if (vorhanden == null) {
                artikelDao.insert(ziel);
            } else {
                artikelDao.update(ziel);
            }
            ergebnis.uebernommen++;
        }
    }

    /**
     * Uebernimmt das Protokoll der anderen Geraete und der Weboberflaeche -
     * damit jeder Artikel ueberall denselben Verlauf zeigt. Die eigenen
     * Eintraege kommen dabei zurueck; die erkennt man an Artikel, Aktion und
     * Zeitpunkt und merkt sich nur ihre Server-Kennung.
     */
    private static void protokollUebernehmen(@Nullable JSONArray eintraege,
                                             ArtikelDao artikelDao, ProtokollDao protokollDao) {
        if (eintraege == null) {
            return;
        }
        Map<String, Long> artikelIds = new HashMap<>();
        for (int i = 0; i < eintraege.length(); i++) {
            JSONObject o = eintraege.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String serverId = leer(o.optString("id", null));
            String artikelServerId = leer(o.optString("artikelId", null));
            if (serverId == null || artikelServerId == null
                    || protokollDao.nachServerId(serverId) != null) {
                continue;
            }
            if (!artikelIds.containsKey(artikelServerId)) {
                Artikel artikel = artikelDao.nachServerId(artikelServerId);
                artikelIds.put(artikelServerId, artikel == null ? null : artikel.id);
            }
            Long artikelId = artikelIds.get(artikelServerId);
            if (artikelId == null) {
                // Den Artikel gibt es hier nicht (mehr).
                continue;
            }
            String aktion = o.optString("aktion", "");
            long zeitpunkt = o.optLong("zeitpunkt");
            Protokoll eigener = protokollDao.eigenerOhneKennung(artikelId, aktion, zeitpunkt);
            if (eigener != null) {
                eigener.serverId = serverId;
                protokollDao.update(eigener);
                continue;
            }
            Protokoll eintrag = new Protokoll();
            eintrag.serverId = serverId;
            eintrag.artikelId = artikelId;
            eintrag.artikelName = o.optString("artikelName", "");
            eintrag.aktion = aktion;
            eintrag.alterWert = leer(o.optString("alterWert", null));
            eintrag.neuerWert = leer(o.optString("neuerWert", null));
            eintrag.nutzer = leer(o.optString("nutzer", null));
            eintrag.zeitpunkt = zeitpunkt;
            eintrag.offen = false;
            protokollDao.insert(eintrag);
        }
    }

    /**
     * Uebernimmt die Kategorien des Teams: neue kommen dazu, umbenannte werden
     * nachgezogen, geloeschte verschwinden auch hier.
     */
    private static void kategorienUebernehmen(@Nullable JSONArray kategorien, String teamId,
                                              KategorieDao kategorieDao) {
        if (kategorien == null) {
            return;
        }
        List<Kategorie> alle = new ArrayList<>(kategorieDao.alle());
        for (int i = 0; i < kategorien.length(); i++) {
            JSONObject o = kategorien.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String serverId = o.optString("id", "");
            String name = o.optString("name", "");
            if (serverId.isEmpty() || name.isEmpty()) {
                continue;
            }

            Kategorie treffer = null;
            for (Kategorie kategorie : alle) {
                if (serverId.equals(kategorie.serverId)
                        || (kategorie.serverId == null && kategorie.name.equalsIgnoreCase(name))) {
                    treffer = kategorie;
                    break;
                }
            }

            if (o.optBoolean("geloescht")) {
                if (treffer != null) {
                    kategorieDao.delete(treffer);
                    alle.remove(treffer);
                }
                continue;
            }

            int reihenfolge = o.optInt("reihenfolge", alle.size());
            try {
                if (treffer == null) {
                    Kategorie neue = new Kategorie(name, reihenfolge);
                    neue.serverId = serverId;
                    neue.teamId = teamId;
                    kategorieDao.insert(neue);
                    alle.add(neue);
                    continue;
                }
                treffer.serverId = serverId;
                treffer.teamId = teamId;
                treffer.name = name;
                treffer.reihenfolge = reihenfolge;
                kategorieDao.update(treffer);
            } catch (RuntimeException doppelt) {
                // Den Namen gibt es hier schon - dann bleibt es bei der eigenen Fassung.
                continue;
            }
        }
    }

    @Nullable
    private static String leer(@Nullable String wert) {
        return wert == null || wert.trim().isEmpty() || "null".equals(wert) ? null : wert.trim();
    }
}
