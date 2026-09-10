package de.tagstock.data;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import de.tagstock.util.Abgleichplaner;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Einziger Zugriffspunkt auf die Datenbank. Schreibende und lesende Einzelabfragen
 * laufen im Hintergrund, Ergebnisse kommen auf dem Main-Thread zurueck.
 */
public class Repository {

    public interface Callback<T> {
        void onResult(T ergebnis);
    }

    /** Ergebnis des Speicherns; bei belegter Kennung steht der Gegenspieler drin. */
    public static class Speicherergebnis {
        public final boolean erfolgreich;
        public final long id;
        @Nullable
        public final Artikel kennungBelegtVon;

        Speicherergebnis(boolean erfolgreich, long id, @Nullable Artikel kennungBelegtVon) {
            this.erfolgreich = erfolgreich;
            this.id = id;
            this.kennungBelegtVon = kennungBelegtVon;
        }
    }

    private static volatile Repository instance;

    private final AppDatabase db;
    private final ArtikelDao artikelDao;
    private final Context context;
    private final KategorieDao kategorieDao;
    private final ProtokollDao protokollDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Repository(Context context) {
        this.context = context.getApplicationContext();
        db = AppDatabase.getInstance(context);
        artikelDao = db.artikelDao();
        kategorieDao = db.kategorieDao();
        protokollDao = db.protokollDao();
    }

    public static Repository getInstance(Context context) {
        if (instance == null) {
            synchronized (Repository.class) {
                if (instance == null) {
                    instance = new Repository(context);
                }
            }
        }
        return instance;
    }

    /** Arbeitsschritt, der direkt auf den Tabellen arbeitet. */
    public interface Arbeit<T> {
        T mache(ArtikelDao artikel, KategorieDao kategorien, ProtokollDao protokoll)
                throws Exception;
    }

    /**
     * Fuehrt eine zusammenhaengende Aufgabe im Hintergrund aus - benutzt der
     * Abgleich, der Datenbank und Server in einem Rutsch braucht.
     */
    public <T> void imHintergrund(Arbeit<T> arbeit, Callback<T> callback) {
        starte(() -> arbeit.mache(artikelDao, kategorieDao, protokollDao), callback);
    }

    /** Dieselbe Aufgabe blockierend - fuer Aufrufer, die schon im Hintergrund laufen. */
    @androidx.annotation.WorkerThread
    public <T> T sofort(Arbeit<T> arbeit) throws Exception {
        return arbeit.mache(artikelDao, kategorieDao, protokollDao);
    }

    // ---------------------------------------------------------------- Abfragen

    public LiveData<List<Artikel>> beobachteArtikel() {
        return artikelDao.beobachteAlle();
    }

    public LiveData<Artikel> beobachteArtikel(long id) {
        return artikelDao.beobachte(id);
    }

    public LiveData<List<String>> beobachteStandorte() {
        return artikelDao.beobachteStandorte();
    }

    public LiveData<List<Kategorie>> beobachteKategorien() {
        return kategorieDao.beobachteAlle();
    }

    public LiveData<List<Protokoll>> beobachteProtokoll(long artikelId) {
        return protokollDao.beobachteFuerArtikel(artikelId);
    }

    public void ladeArtikel(long id, Callback<Artikel> callback) {
        starte(() -> artikelDao.nachId(id), callback);
    }

    public void ladeAlleArtikel(Callback<List<Artikel>> callback) {
        starte(artikelDao::alle, callback);
    }

    public void ladeKategorien(Callback<List<Kategorie>> callback) {
        starte(kategorieDao::alle, callback);
    }

    /**
     * Direkte Suche nach einer Kennung. Blockiert und gehoert deshalb in einen
     * Hintergrund-Thread - etwa den, in dem ein NFC-Tag verarbeitet wird.
     */
    @androidx.annotation.WorkerThread
    @Nullable
    public Artikel kennungDirekt(String wert) {
        return wert == null || wert.trim().isEmpty() ? null : artikelDao.nachKennung(wert.trim());
    }

    /** Wie viele Artikel warten noch auf die Uebertragung zum Server? */
    public void zaehleOffene(Callback<Integer> callback) {
        starte(artikelDao::anzahlOffene, callback);
    }

    /** Sucht den Artikel zu einem gescannten Wert; prueft mehrere Kandidaten. */
    public void findeNachKennung(List<String> werte, Callback<Artikel> callback) {
        starte(() -> {
            for (String wert : werte) {
                if (wert == null || wert.trim().isEmpty()) {
                    continue;
                }
                Artikel treffer = artikelDao.nachKennung(wert.trim());
                if (treffer != null) {
                    return treffer;
                }
            }
            return null;
        }, callback);
    }

    // --------------------------------------------------------------- Schreiben

    /**
     * Legt einen Artikel an oder aktualisiert ihn und schreibt die Aenderungen
     * ins Protokoll. Ist die Kennung schon vergeben, wird nichts gespeichert.
     */
    public void speichern(Artikel neu, @Nullable Artikel vorher, String nutzer,
                          Callback<Speicherergebnis> callback) {
        starte(() -> {
            if (neu.rfidUid != null && !neu.rfidUid.isEmpty()) {
                Artikel belegt = artikelDao.nachKennung(neu.rfidUid);
                if (belegt != null && belegt.id != neu.id) {
                    return new Speicherergebnis(false, 0L, belegt);
                }
            }
            neu.geaendertAm = System.currentTimeMillis();
            neu.offen = true;
            try {
                if (neu.id == 0L) {
                    long id = artikelDao.insert(neu);
                    neu.id = id;
                    eintragen(neu, Protokoll.ANGELEGT, null, neu.name, nutzer);
                    return new Speicherergebnis(true, id, null);
                }
                artikelDao.update(neu);
                if (vorher != null) {
                    protokolliereUnterschiede(vorher, neu, nutzer);
                }
                return new Speicherergebnis(true, neu.id, null);
            } catch (SQLiteConstraintException e) {
                Artikel belegt = neu.rfidUid == null ? null : artikelDao.nachKennung(neu.rfidUid);
                return new Speicherergebnis(false, 0L, belegt);
            }
        }, callback);
    }

    private void protokolliereUnterschiede(Artikel vorher, Artikel neu, String nutzer) {
        if (!vorher.name.equals(neu.name)) {
            eintragen(neu, Protokoll.NAME, vorher.name, neu.name, nutzer);
        }
        if (vorher.status != neu.status) {
            eintragen(neu, Protokoll.STATUS, schluessel(vorher.status), schluessel(neu.status), nutzer);
        }
        if (!gleich(vorher.standort, neu.standort)) {
            eintragen(neu, Protokoll.STANDORT, vorher.standort, neu.standort, nutzer);
        }
        if (!gleich(vorher.lagerort, neu.lagerort)) {
            eintragen(neu, Protokoll.LAGERORT, vorher.lagerort, neu.lagerort, nutzer);
        }
    }

    /** Setzt den Status eines Artikels und schreibt einen Protokolleintrag. */
    public void statusSetzen(Artikel artikel, ArtikelStatus status, String nutzer,
                             @Nullable Callback<Boolean> callback) {
        starte(() -> {
            Artikel aktuell = artikelDao.nachId(artikel.id);
            if (aktuell == null || aktuell.status == status) {
                return false;
            }
            ArtikelStatus alt = aktuell.status;
            aktuell.status = status;
            if (status != ArtikelStatus.VERLIEHEN) {
                aktuell.verliehenAn = null;
                aktuell.rueckgabeDatum = null;
            }
            aktuell.geaendertAm = System.currentTimeMillis();
            aktuell.offen = true;
            artikelDao.update(aktuell);
            eintragen(aktuell, Protokoll.STATUS, schluessel(alt), schluessel(status), nutzer);
            return true;
        }, ergebnis -> {
            if (callback != null) {
                callback.onResult(ergebnis);
            }
        });
    }

    /** Setzt den Standort eines Artikels. */
    public void standortSetzen(Artikel artikel, String standort, String nutzer,
                               @Nullable Callback<Boolean> callback) {
        starte(() -> {
            Artikel aktuell = artikelDao.nachId(artikel.id);
            if (aktuell == null || gleich(aktuell.standort, standort)) {
                return false;
            }
            String alt = aktuell.standort;
            aktuell.standort = standort;
            aktuell.geaendertAm = System.currentTimeMillis();
            aktuell.offen = true;
            artikelDao.update(aktuell);
            eintragen(aktuell, Protokoll.STANDORT, alt, standort, nutzer);
            return true;
        }, ergebnis -> {
            if (callback != null) {
                callback.onResult(ergebnis);
            }
        });
    }

    /** Setzt den Status mehrerer Artikel in einem Rutsch. */
    public void mehrfachStatus(List<Long> ids, ArtikelStatus status, String nutzer,
                               Callback<Integer> callback) {
        // In einer Transaktion: sonst schreibt SQLite jede Zeile einzeln auf die Platte.
        starte(() -> db.runInTransaction(() -> {
            int geaendert = 0;
            for (Long id : ids) {
                Artikel artikel = artikelDao.nachId(id);
                if (artikel == null || artikel.status == status) {
                    continue;
                }
                ArtikelStatus alt = artikel.status;
                artikel.status = status;
                if (status != ArtikelStatus.VERLIEHEN) {
                    artikel.verliehenAn = null;
                    artikel.rueckgabeDatum = null;
                }
                artikel.geaendertAm = System.currentTimeMillis();
                artikel.offen = true;
                artikelDao.update(artikel);
                eintragen(artikel, Protokoll.STATUS, schluessel(alt), schluessel(status), nutzer);
                geaendert++;
            }
            return geaendert;
        }), callback);
    }

    /** Verschiebt mehrere Artikel an einen anderen Standort. */
    public void mehrfachStandort(List<Long> ids, String standort, String nutzer,
                                 Callback<Integer> callback) {
        starte(() -> db.runInTransaction(() -> {
            int geaendert = 0;
            for (Long id : ids) {
                Artikel artikel = artikelDao.nachId(id);
                if (artikel == null || gleich(artikel.standort, standort)) {
                    continue;
                }
                String alt = artikel.standort;
                artikel.standort = standort;
                artikel.geaendertAm = System.currentTimeMillis();
                artikel.offen = true;
                artikelDao.update(artikel);
                eintragen(artikel, Protokoll.STANDORT, alt, standort, nutzer);
                geaendert++;
            }
            return geaendert;
        }), callback);
    }

    /**
     * Vermerkt einen Scan: Zeitstempel setzen und - falls der Artikel als nicht
     * vorhanden galt - wieder auf vorhanden buchen.
     */
    public void alsGescanntBuchen(long artikelId, String nutzer, Callback<Artikel> callback) {
        starte(() -> {
            Artikel artikel = artikelDao.nachId(artikelId);
            if (artikel == null) {
                return null;
            }
            boolean statusWechsel = artikel.status == ArtikelStatus.NICHT_VORHANDEN;
            ArtikelStatus alt = artikel.status;
            if (statusWechsel) {
                artikel.status = ArtikelStatus.VORHANDEN;
            }
            artikel.zuletztGescannt = System.currentTimeMillis();
            artikel.geaendertAm = artikel.zuletztGescannt;
            artikel.offen = true;
            artikelDao.update(artikel);
            if (statusWechsel) {
                eintragen(artikel, Protokoll.STATUS, schluessel(alt),
                        schluessel(ArtikelStatus.VORHANDEN), nutzer);
            } else {
                eintragen(artikel, Protokoll.GESCANNT, null, null, nutzer);
            }
            return artikel;
        }, callback);
    }

    public void loeschen(Artikel artikel) {
        executor.execute(() -> artikelDao.delete(artikel));
    }

    // -------------------------------------------------------------- Kategorien

    public void kategorieAnlegen(String name, Callback<Boolean> callback) {
        kategorieAnlegen(name, null, callback);
    }

    /** Legt eine Kategorie an; mit Server-Kennung, wenn sie dort schon liegt. */
    public void kategorieAnlegen(String name, @Nullable String serverId,
                                 Callback<Boolean> callback) {
        starte(() -> {
            List<Kategorie> vorhandene = kategorieDao.alle();
            for (Kategorie kategorie : vorhandene) {
                if (kategorie.name.equalsIgnoreCase(name)) {
                    return false;
                }
            }
            Kategorie neue = new Kategorie(name, vorhandene.size());
            neue.serverId = serverId;
            neue.teamId = kategorieTeam(vorhandene);
            return kategorieDao.insert(neue) > 0;
        }, callback);
    }

    @Nullable
    private String kategorieTeam(List<Kategorie> vorhandene) {
        for (Kategorie kategorie : vorhandene) {
            if (kategorie.teamId != null) {
                return kategorie.teamId;
            }
        }
        return null;
    }

    public void kategorieUmbenennen(Kategorie kategorie, String name) {
        executor.execute(() -> {
            kategorie.name = name;
            kategorieDao.update(kategorie);
        });
    }

    public void kategorieLoeschen(Kategorie kategorie) {
        executor.execute(() -> kategorieDao.delete(kategorie));
    }

    public void kategorienVerschieben(List<Kategorie> reihenfolge) {
        executor.execute(() -> {
            for (int i = 0; i < reihenfolge.size(); i++) {
                Kategorie kategorie = reihenfolge.get(i);
                kategorie.reihenfolge = i;
                kategorieDao.update(kategorie);
            }
        });
    }

    // --------------------------------------------------------------- Sicherung

    public void ladeBestand(Callback<Bestand> callback) {
        starte(() -> new Bestand(artikelDao.alle(), kategorieDao.alle(), protokollDao.alle()),
                callback);
    }

    /** Ersetzt den gesamten Bestand durch die eingelesenen Daten. */
    public void bestandErsetzen(Bestand bestand, Callback<Integer> callback) {
        starte(() -> {
            db.runInTransaction(() -> {
                protokollDao.alleLoeschen();
                artikelDao.alleLoeschen();
                kategorieDao.alleLoeschen();
                for (Kategorie kategorie : bestand.kategorien) {
                    kategorieDao.insert(kategorie);
                }
                for (Artikel artikel : bestand.artikel) {
                    artikelDao.insert(artikel);
                }
                for (Protokoll eintrag : bestand.protokoll) {
                    protokollDao.insert(eintrag);
                }
            });
            return bestand.artikel.size();
        }, callback);
    }

    /** Fuegt eingelesene Artikel zum vorhandenen Bestand hinzu. */
    public void bestandErgaenzen(Bestand bestand, Callback<Integer> callback) {
        starte(() -> {
            final int[] neu = {0};
            db.runInTransaction(() -> {
                for (Kategorie kategorie : bestand.kategorien) {
                    kategorie.id = 0;
                    kategorieDao.insert(kategorie);
                }
                for (Artikel artikel : bestand.artikel) {
                    if (artikel.rfidUid != null && artikelDao.nachKennung(artikel.rfidUid) != null) {
                        // Kennung ist schon vergeben - Artikel ohne sie uebernehmen.
                        artikel.rfidUid = null;
                    }
                    artikel.id = 0;
                    artikelDao.insert(artikel);
                    neu[0]++;
                }
            });
            return neu[0];
        }, callback);
    }

    // ----------------------------------------------------------------- Hilfen

    /**
     * Jede Aenderung landet im Protokoll - und ist damit auch der richtige Ort,
     * um die Uebertragung zum Server vorzumerken. Ohne Netz wartet sie, bis
     * wieder eine Verbindung da ist.
     */
    private void eintragen(Artikel artikel, String aktion, String alt, String neu, String nutzer) {
        protokollDao.insert(Protokoll.fuer(artikel, aktion, alt, neu, nutzer));
        Abgleichplaner.vormerken(context);
    }

    private String schluessel(ArtikelStatus status) {
        return status == null ? null : status.schluessel;
    }

    private boolean gleich(String a, String b) {
        String linke = a == null ? "" : a.trim();
        String rechte = b == null ? "" : b.trim();
        return linke.equals(rechte);
    }

    private <T> void starte(java.util.concurrent.Callable<T> arbeit, Callback<T> callback) {
        executor.execute(() -> {
            final T ergebnis;
            try {
                ergebnis = arbeit.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            mainHandler.post(() -> callback.onResult(ergebnis));
        });
    }
}
