package de.tagstock.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.tagstock.data.ItemStatus;
import de.tagstock.data.ItemWithState;
import de.tagstock.data.Lager;
import de.tagstock.data.LagerWithCount;
import de.tagstock.data.Repository;

/** Suche ueber alle Lager hinweg. */
public class SucheViewModel extends AndroidViewModel {

    private final MediatorLiveData<List<ItemWithState>> ergebnis = new MediatorLiveData<>();
    private final MediatorLiveData<Map<Long, String>> lagerNamen = new MediatorLiveData<>();

    private List<ItemWithState> alle = Collections.emptyList();
    private String suche = "";
    @Nullable
    private ItemStatus filter;

    public SucheViewModel(@NonNull Application application) {
        super(application);
        Repository repository = Repository.getInstance(application);

        ergebnis.addSource(repository.observeAlleItems(), items -> {
            alle = items == null ? Collections.emptyList() : items;
            anwenden();
        });

        LiveData<List<LagerWithCount>> lager = repository.observeLagerWithCounts();
        lagerNamen.addSource(lager, liste -> {
            Map<Long, String> namen = new HashMap<>();
            if (liste != null) {
                for (LagerWithCount eintrag : liste) {
                    Lager l = eintrag.lager;
                    namen.put(l.id, l.name);
                }
            }
            lagerNamen.setValue(namen);
        });
    }

    public LiveData<List<ItemWithState>> getErgebnis() {
        return ergebnis;
    }

    public LiveData<Map<Long, String>> getLagerNamen() {
        return lagerNamen;
    }

    public void setSuche(String wert) {
        suche = wert == null ? "" : wert.trim().toLowerCase(Locale.getDefault());
        anwenden();
    }

    public void setFilter(@Nullable ItemStatus status) {
        filter = status;
        anwenden();
    }

    public boolean istLeererFilter() {
        return suche.isEmpty() && filter == null;
    }

    private void anwenden() {
        if (istLeererFilter()) {
            // Ohne Suchbegriff nicht den gesamten Bestand aufblaettern.
            ergebnis.setValue(Collections.emptyList());
            return;
        }
        ergebnis.setValue(ItemListViewModel.Filter.anwenden(alle, suche, filter));
    }
}
