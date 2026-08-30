package de.tagstock.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.tagstock.data.Code;
import de.tagstock.data.ItemStatus;
import de.tagstock.data.ItemWithState;
import de.tagstock.data.Repository;

/** Artikel eines Lagers, gefiltert nach Suchtext und Zustand. */
public class ItemListViewModel extends AndroidViewModel {

    private final MutableLiveData<Long> lagerId = new MutableLiveData<>();
    private final MediatorLiveData<List<ItemWithState>> gefiltert = new MediatorLiveData<>();

    private List<ItemWithState> alle = Collections.emptyList();
    private String suche = "";
    @Nullable
    private ItemStatus filter;

    public ItemListViewModel(@NonNull Application application) {
        super(application);
        Repository repository = Repository.getInstance(application);
        LiveData<List<ItemWithState>> quelle =
                Transformations.switchMap(lagerId, repository::observeItems);
        gefiltert.addSource(quelle, items -> {
            alle = items == null ? Collections.emptyList() : items;
            anwenden();
        });
    }

    public void setLagerId(long id) {
        Long aktuell = lagerId.getValue();
        if (aktuell == null || aktuell != id) {
            lagerId.setValue(id);
        }
    }

    public LiveData<List<ItemWithState>> getItems() {
        return gefiltert;
    }

    public void setSuche(String wert) {
        suche = wert == null ? "" : wert.trim().toLowerCase(Locale.getDefault());
        anwenden();
    }

    public void setFilter(@Nullable ItemStatus status) {
        filter = status;
        anwenden();
    }

    /** true, wenn das Lager ueberhaupt Artikel enthaelt (unabhaengig vom Filter). */
    public boolean hatItems() {
        return !alle.isEmpty();
    }

    private void anwenden() {
        gefiltert.setValue(Filter.anwenden(alle, suche, filter));
    }

    /** Gemeinsame Filterlogik von Lagerliste und Suche. */
    public static final class Filter {

        private Filter() {
        }

        public static List<ItemWithState> anwenden(List<ItemWithState> quelle, String suche,
                                                   @Nullable ItemStatus filter) {
            List<ItemWithState> ergebnis = new ArrayList<>();
            for (ItemWithState state : quelle) {
                if (filter != null && !state.hat(filter)) {
                    continue;
                }
                if (!suche.isEmpty() && !passt(state, suche)) {
                    continue;
                }
                ergebnis.add(state);
            }
            return ergebnis;
        }

        public static boolean passt(ItemWithState state, String suche) {
            if (enthaelt(state.item.name, suche)
                    || enthaelt(state.item.beschreibung, suche)
                    || enthaelt(state.item.notiz, suche)) {
                return true;
            }
            if (state.codes != null) {
                for (Code code : state.codes) {
                    if (enthaelt(code.wert, suche)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean enthaelt(@Nullable String wert, String suche) {
            return wert != null && wert.toLowerCase(Locale.getDefault()).contains(suche);
        }
    }
}
