package de.tagstock.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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

    private static volatile Repository instance;

    private final LagerDao lagerDao;
    private final ItemDao itemDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Repository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        lagerDao = db.lagerDao();
        itemDao = db.itemDao();
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
        run(() -> lagerDao.getAll(), callback);
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

    // ----------------------------------------------------------------- Artikel

    public LiveData<List<Item>> observeItems(long lagerId) {
        return itemDao.observeByLager(lagerId);
    }

    public LiveData<List<Item>> observeAllItems() {
        return itemDao.observeAll();
    }

    public void loadItem(long id, Callback<Item> callback) {
        run(() -> itemDao.getById(id), callback);
    }

    public void insertItem(Item item, Callback<Long> callback) {
        run(() -> itemDao.insert(item), callback);
    }

    public void updateItem(Item item) {
        item.geaendertAm = System.currentTimeMillis();
        executor.execute(() -> itemDao.update(item));
    }

    public void deleteItem(Item item) {
        executor.execute(() -> itemDao.delete(item));
    }

    /** Sucht einen Artikel zu einem gescannten Code ueber alle Lager hinweg. */
    public void findByCode(String code, Callback<Item> callback) {
        run(() -> itemDao.findByCode(code), callback);
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
