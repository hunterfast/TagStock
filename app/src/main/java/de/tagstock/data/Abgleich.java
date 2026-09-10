package de.tagstock.data;

import android.content.Context;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.tagstock.util.Einstellungen;
import de.tagstock.util.Fotos;
import de.tagstock.util.ServerClient;
import de.tagstock.util.Serverbilder;

/**
 * Abgleich zwischen App und Server: eigene Aenderungen hochladen, fremde
 * uebernehmen. Auch die Artikelbilder gehen dabei auf den Server; auf dem
 * Geraet bleibt nur ein Zwischenspeicher.
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

        public boolean erfolgreich() {
            return fehler == null;
        }
    }

    private Abgleich() {
    }

    /** Fuehrt einen vollstaendigen Abgleich aus; die Antwort kommt im Hauptthread. */
    public static void ausfuehren(Context context, Repository.Callback<Ergebnis> callback) {
        Repository repository = Repository.getInstance(context);
        Ergebnis vorab = vorpruefung(context);
        if (vorab != null) {
            callback.onResult(vorab);
            return;
        }
        repository.imHintergrund((artikelDao, kategorieDao, protokollDao) ->
                durchfuehren(context, artikelDao, kategorieDao, protokollDao), callback);
    }

    /**
     * Derselbe Abgleich, nur blockierend - dafuer aus einem Hintergrund-Thread
     * aufzurufen. Nutzt die geplante Uebertragung, sobald wieder Netz da ist.
     */
    @androidx.annotation.WorkerThread
    public static Ergebnis jetzt(Context context) {
        Ergebnis vorab = vorpruefung(context);
        if (vorab != null) {
            return vorab;
        }
        try {
            return Repository.getInstance(context).sofort(
                    (artikelDao, kategorieDao, protokollDao) ->
                            durchfuehren(context, artikelDao, kategorieDao, protokollDao));
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

    /** Zwei Abgleiche gleichzeitig braucht niemand - der zweite wartet nicht, er faellt aus. */
    private static final java.util.concurrent.atomic.AtomicBoolean LAEUFT =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private static Ergebnis durchfuehren(Context context, ArtikelDao artikelDao,
                                         KategorieDao kategorieDao, ProtokollDao protokollDao) {
        Ergebnis ergebnis = new Ergebnis();
        if (!LAEUFT.compareAndSet(false, true)) {
            ergebnis.fehler = "Abgleich laeuft bereits";
            ergebnis.laeuftSchon = true;
            return ergebnis;
        }
        String url = Einstellungen.serverUrl(context);
        String token = Einstellungen.token(context);
        String teamId = Einstellungen.teamId(context);
        long seit = Einstellungen.letzterSync(context);
        ServerClient client = new ServerClient(url, token);
        try {
            // Zuerst die Bilder der bereits bekannten Artikel, damit die
            // Adresse gleich mit hochgeht.
            bilderHochladen(context, client, teamId, artikelDao, ergebnis);

            JSONArray offeneArtikel = new JSONArray();
            List<Artikel> offene = artikelDao.offene();
            for (Artikel artikel : offene) {
                offeneArtikel.put(alsJson(artikel));
            }

            JSONArray offeneEintraege = new JSONArray();
            List<Long> gesendeteEintraege = new ArrayList<>();
            java.util.Map<Long, String> kennungen = new java.util.HashMap<>();
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

            zuordnungenAnwenden(antwort.optJSONArray("zuordnungen"), artikelDao, ergebnis);
            serverstandUebernehmen(antwort.optJSONArray("artikel"), teamId, artikelDao, ergebnis);
            kategorienUebernehmen(antwort.optJSONArray("kategorien"), teamId, kategorieDao);

            if (!gesendeteEintraege.isEmpty()) {
                protokollDao.alsGesendetMarkieren(gesendeteEintraege);
            }

            // Neu angelegte Artikel haben jetzt eine Server-Kennung - ihre
            // Bilder koennen nachgereicht werden.
            bilderHochladen(context, client, teamId, artikelDao, ergebnis);

            long stand = antwort.optLong("stand", System.currentTimeMillis());
            Einstellungen.setzeLetztenSync(context, stand);
        } catch (ServerClient.ServerFehler fehler) {
            ergebnis.fehler = fehler.getMessage();
            ergebnis.abgemeldet = fehler.istAbgemeldet();
        } catch (Exception fehler) {
            ergebnis.fehler = fehler.getMessage() == null
                    ? "Server nicht erreichbar" : fehler.getMessage();
        } finally {
            LAEUFT.set(false);
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
        o.put("erstelltAm", artikel.erstelltAm);
        o.put("geaendertAm", artikel.geaendertAm);
        return o;
    }

    private static JSONObject alsJson(Protokoll eintrag, @Nullable String artikelServerId)
            throws JSONException {
        JSONObject o = new JSONObject();
        o.put("artikelId", artikelServerId);
        o.put("artikelName", eintrag.artikelName);
        o.put("aktion", eintrag.aktion);
        o.put("alterWert", eintrag.alterWert);
        o.put("neuerWert", eintrag.neuerWert);
        o.put("nutzer", eintrag.nutzer);
        o.put("zeitpunkt", eintrag.zeitpunkt);
        return o;
    }

    // ------------------------------------------------------------------ Bilder

    /**
     * Schickt Aufnahmen, die noch nur auf dem Geraet liegen, an den Server und
     * merkt sich die Adresse. Danach ist die Datei im Zwischenspeicher; das
     * Original im App-Verzeichnis wird nicht mehr gebraucht.
     */
    private static void bilderHochladen(Context context, ServerClient client, String teamId,
                                        ArtikelDao artikelDao, Ergebnis ergebnis) {
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

                Artikel frisch = artikelDao.nachId(artikel.id);
                if (frisch == null) {
                    continue;
                }
                frisch.bildUrl = adresse;
                frisch.fotoPfad = null;
                // Der Server hat den Artikel beim Ablegen angefasst; ohne diesen
                // Stand wuerde er unsere eigenen Aenderungen gleich abweisen.
                long stand = antwort.optLong("geaendertAm", 0L);
                if (stand > frisch.geaendertAm) {
                    frisch.geaendertAm = stand;
                }
                artikelDao.update(frisch);
                ergebnis.bilder++;
            } catch (Exception fehler) {
                // Beim naechsten Abgleich noch einmal versuchen.
                return;
            }
        }
    }

    // ---------------------------------------------------------------- Annehmen

    /** Traegt die Server-Kennungen ein und merkt sich, was abgelehnt wurde. */
    private static void zuordnungenAnwenden(@Nullable JSONArray zuordnungen, ArtikelDao artikelDao,
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
            if (serverId != null && !serverId.isEmpty()) {
                artikel.serverId = serverId;
            }
            // In beiden Faellen ist der Artikel abgearbeitet: bei Ablehnung
            // gewinnt der Serverstand, der gleich mit uebernommen wird.
            artikel.offen = false;
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

    /** Uebernimmt die Artikel, die vom Server kommen. */
    private static void serverstandUebernehmen(@Nullable JSONArray artikelListe, String teamId,
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
            if (serverId.isEmpty()) {
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
            ziel.erstelltAm = o.optLong("erstelltAm", System.currentTimeMillis());
            ziel.geaendertAm = o.optLong("geaendertAm", ziel.erstelltAm);
            ziel.offen = false;

            if (vorhanden == null) {
                artikelDao.insert(ziel);
            } else {
                artikelDao.update(ziel);
            }
            ergebnis.uebernommen++;
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
