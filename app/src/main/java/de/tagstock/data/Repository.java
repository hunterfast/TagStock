package de.tagstock.data;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Einziger Zugriffspunkt auf die Datenbank. Schreibende und lesende Einzelabfragen
 * laufen im Hintergrund, Ergebnisse werden auf dem Main-Thread zurueckgegeben.
 */
public class Repository {

    /** Rueckgabe einer Hintergrundabfrage auf dem Main-Thread. */
    public interface Callback<T> {
        void onResult(T result);
    }

    /** Treffer einer Code-Suche samt Lager, in dem der Artikel liegt. */
    public static class Treffer {
        public final ItemWithState item;
        public final Lager lager;

        public Treffer(ItemWithState item, Lager lager) {
            this.item = item;
            this.lager = lager;
        }
    }

    /** Ergebnis beim Hinterlegen eines Codes. */
    public static class CodeVergabe {
        public final boolean erfolgreich;
        /** Bei Misserfolg: der Artikel, der den Code bereits belegt. */
        @Nullable
        public final Treffer belegtVon;

        CodeVergabe(boolean erfolgreich, @Nullable Treffer belegtVon) {
            this.erfolgreich = erfolgreich;
            this.belegtVon = belegtVon;
        }
    }

    private static volatile Repository instance;

    private final AppDatabase db;
    private final LagerDao lagerDao;
    private final ItemDao itemDao;
    private final CodeDao codeDao;
    private final VerleihDao verleihDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Repository(Context context) {
        db = AppDatabase.getInstance(context);
        lagerDao = db.lagerDao();
        itemDao = db.itemDao();
        codeDao = db.codeDao();
        verleihDao = db.verleihDao();
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

    // ------------------------------------------------------------------ Lager

    public LiveData<List<LagerWithCount>> observeLagerWithCounts() {
        return lagerDao.observeAllWithCounts();
    }

    public LiveData<Lager> observeLager(long id) {
        return lagerDao.observeById(id);
    }

    public void loadAllLager(Callback<List<Lager>> callback) {
        run(lagerDao::getAll, callback);
    }

    public void loadLager(long id, Callback<Lager> callback) {
        run(() -> lagerDao.getById(id), callback);
    }

    public void insertLager(Lager lager, Callback<Long> callback) {
        run(() -> lagerDao.insert(lager), callback);
    }

    public void updateLager(Lager lager) {
        executor.execute(() -> lagerDao.update(lager));
    }

    public void deleteLager(Lager lager) {
        executor.execute(() -> lagerDao.delete(lager));
    }

    // ---------------------------------------------------------------- Artikel

    public LiveData<List<ItemWithState>> observeItems(long lagerId) {
        return itemDao.observeByLager(lagerId);
    }

    public LiveData<List<ItemWithState>> observeAlleItems() {
        return itemDao.observeAll();
    }

    public LiveData<ItemWithState> observeItemState(long id) {
        return itemDao.observeState(id);
    }

    public void ladeAlleZustaende(Callback<List<ItemWithState>> callback) {
        run(itemDao::getAllStates, callback);
    }

    public void ladeZustaendeVonLager(long lagerId, Callback<List<ItemWithState>> callback) {
        run(() -> itemDao.getByLager(lagerId), callback);
    }

    public void loadItem(long id, Callback<Item> callback) {
        run(() -> itemDao.getById(id), callback);
    }

    public void loadItemState(long id, Callback<ItemWithState> callback) {
        run(() -> itemDao.getState(id), callback);
    }

    public void insertItem(Item item, Callback<Long> callback) {
        run(() -> itemDao.insert(item), callback);
    }

    public void updateItem(Item item) {
        item.geaendertAm = System.currentTimeMillis();
        executor.execute(() -> itemDao.update(item));
    }

    public void updateItem(Item item, Runnable danach) {
        item.geaendertAm = System.currentTimeMillis();
        run(() -> {
            itemDao.update(item);
            return null;
        }, ignored -> danach.run());
    }

    public void deleteItem(Item item) {
        executor.execute(() -> itemDao.delete(item));
    }

    // ------------------------------------------------------------------ Codes

    public LiveData<List<Code>> observeCodes(long itemId) {
        return codeDao.observeByItem(itemId);
    }

    /**
     * Hinterlegt einen Code an einem Artikel. Ist der Wert bereits vergeben,
     * meldet das Ergebnis, welcher Artikel ihn belegt.
     */
    public void codeHinzufuegen(long itemId, String wert, CodeType typ,
                                Callback<CodeVergabe> callback) {
        run(() -> {
            try {
                codeDao.insert(new Code(itemId, wert, typ));
                return new CodeVergabe(true, null);
            } catch (SQLiteConstraintException e) {
                return new CodeVergabe(false, suchen(wert));
            }
        }, callback);
    }

    public void codeEntfernen(Code code) {
        executor.execute(() -> codeDao.delete(code));
    }

    /** Sucht den Artikel zu einem gescannten Wert - UID oder Tag-Inhalt. */
    public void findeZuCode(List<String> werte, Callback<Treffer> callback) {
        run(() -> {
            for (String wert : werte) {
                if (wert == null || wert.isEmpty()) {
                    continue;
                }
                Treffer treffer = suchen(wert);
                if (treffer != null) {
                    return treffer;
                }
            }
            return null;
        }, callback);
    }

    @Nullable
    private Treffer suchen(String wert) {
        Code code = codeDao.findByWert(wert);
        if (code == null) {
            return null;
        }
        ItemWithState item = itemDao.getState(code.itemId);
        if (item == null) {
            return null;
        }
        return new Treffer(item, lagerDao.getById(item.item.lagerId));
    }

    // ---------------------------------------------------------------- Verleih

    public LiveData<List<Verleih>> observeVerleih(long itemId) {
        return verleihDao.observeByItem(itemId);
    }

    public LiveData<List<Verleih>> observeOffeneVerleihe() {
        return verleihDao.observeOffene();
    }

    public void ladeOffeneVerleihe(long itemId, Callback<List<Verleih>> callback) {
        run(() -> verleihDao.getOffene(itemId), callback);
    }

    /** Verleiht Stuecke eines Artikels; begrenzt auf den vorhandenen Bestand. */
    public void ausleihen(long itemId, String person, int menge, @Nullable String notiz,
                          Callback<Boolean> callback) {
        run(() -> {
            ItemWithState state = itemDao.getState(itemId);
            if (state == null || menge < 1 || state.vorhanden() < menge) {
                return false;
            }
            Verleih verleih = new Verleih();
            verleih.itemId = itemId;
            verleih.person = person;
            verleih.menge = menge;
            verleih.notiz = notiz;
            verleihDao.insert(verleih);
            beruehren(itemId);
            return true;
        }, callback);
    }

    public void zurueckgeben(Verleih verleih, Callback<Boolean> callback) {
        run(() -> {
            verleih.zurueckAm = System.currentTimeMillis();
            verleihDao.update(verleih);
            beruehren(verleih.itemId);
            return true;
        }, callback);
    }

    /** Loescht einen Eintrag aus der Historie. */
    public void verleihLoeschen(Verleih verleih) {
        executor.execute(() -> {
            verleihDao.delete(verleih);
            beruehren(verleih.itemId);
        });
    }

    // --------------------------------------------------------------- Verluste

    /** Meldet Stuecke als verloren; begrenzt auf den vorhandenen Bestand. */
    public void verlorenMelden(long itemId, int menge, Callback<Boolean> callback) {
        run(() -> {
            ItemWithState state = itemDao.getState(itemId);
            if (state == null || menge < 1 || state.vorhanden() < menge) {
                return false;
            }
            Item item = state.item;
            item.mengeVerloren += menge;
            item.geaendertAm = System.currentTimeMillis();
            itemDao.update(item);
            return true;
        }, callback);
    }

    /** Nimmt eine Verlustmeldung zurueck - der Artikel ist wieder aufgetaucht. */
    public void verlustZuruecknehmen(long itemId, int menge, Callback<Boolean> callback) {
        run(() -> {
            Item item = itemDao.getById(itemId);
            if (item == null || menge < 1 || item.mengeVerloren < menge) {
                return false;
            }
            item.mengeVerloren -= menge;
            item.geaendertAm = System.currentTimeMillis();
            itemDao.update(item);
            return true;
        }, callback);
    }

    // ----------------------------------------------------------- Sicherung

    /** Liest den gesamten Bestand fuer den Export. */
    public void ladeAlles(Callback<Bestand> callback) {
        run(() -> new Bestand(lagerDao.getAll(), itemDao.getAll(), codeDao.getAll(),
                verleihDao.getAll()), callback);
    }

    /** Ersetzt den gesamten Bestand durch die importierten Daten. */
    public void ersetzeAlles(Bestand bestand, Callback<Boolean> callback) {
        run(() -> {
            db.runInTransaction(() -> {
                // Artikel, Codes und Ausleihen haengen per Fremdschluessel an den
                // Lagern und verschwinden dadurch mit.
                lagerDao.deleteAll();
                itemDao.deleteAll();
                for (Lager lager : bestand.lager) {
                    lagerDao.insert(lager);
                }
                for (Item item : bestand.items) {
                    itemDao.insert(item);
                }
                codeDao.insertAllIgnore(bestand.codes);
                for (Verleih verleih : bestand.verleihe) {
                    verleihDao.insert(verleih);
                }
            });
            return true;
        }, callback);
    }

    /** Fuegt importierte Daten zum bestehenden Bestand hinzu. */
    public void ergaenzeUm(Bestand bestand, Callback<Integer> callback) {
        run(() -> {
            final int[] neu = {0};
            final java.util.Set<Long> erledigt = new java.util.HashSet<>();
            db.runInTransaction(() -> {
                for (Lager lager : bestand.lager) {
                    long importId = lager.importId;
                    lager.id = 0;
                    long lagerId = lagerDao.insert(lager);
                    for (Item item : bestand.items) {
                        if (item.lagerId != importId || !erledigt.add(item.id)) {
                            continue;
                        }
                        long alteId = item.id;
                        item.id = 0;
                        item.lagerId = lagerId;
                        long itemId = itemDao.insert(item);
                        neu[0]++;
                        for (Code code : bestand.codes) {
                            if (code.itemId == alteId) {
                                Code kopie = new Code(itemId, code.wert, code.typ);
                                kopie.erfasstAm = code.erfasstAm;
                                try {
                                    codeDao.insert(kopie);
                                } catch (SQLiteConstraintException ignored) {
                                    // Code ist bereits vergeben - Artikel bleibt ohne ihn.
                                }
                            }
                        }
                        for (Verleih verleih : bestand.verleihe) {
                            if (verleih.itemId == alteId) {
                                verleih.id = 0;
                                verleih.itemId = itemId;
                                verleihDao.insert(verleih);
                            }
                        }
                    }
                }
            });
            return neu[0];
        }, callback);
    }

    /** Setzt den Aenderungszeitstempel, damit Listen sich neu zeichnen. */
    private void beruehren(long itemId) {
        Item item = itemDao.getById(itemId);
        if (item != null) {
            item.geaendertAm = System.currentTimeMillis();
            itemDao.update(item);
        }
    }

    private <T> void run(java.util.concurrent.Callable<T> work, Callback<T> callback) {
        executor.execute(() -> {
            final T result;
            try {
                result = work.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            mainHandler.post(() -> callback.onResult(result));
        });
    }
}
