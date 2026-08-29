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

import de.tagstock.data.Item;
import de.tagstock.data.ItemStatus;
import de.tagstock.data.Repository;

/** Artikel eines Lagers, gefiltert nach Suchtext und Status. */
public class ItemListViewModel extends AndroidViewModel {

    private final MutableLiveData<Long> lagerId = new MutableLiveData<>();
    private final MediatorLiveData<List<Item>> filtered = new MediatorLiveData<>();

    private List<Item> alle = Collections.emptyList();
    private String query = "";
    @Nullable
    private ItemStatus statusFilter;

    public ItemListViewModel(@NonNull Application application) {
        super(application);
        Repository repository = Repository.getInstance(application);
        LiveData<List<Item>> source = Transformations.switchMap(lagerId, repository::observeItems);
        filtered.addSource(source, items -> {
            alle = items == null ? Collections.emptyList() : items;
            apply();
        });
    }

    public void setLagerId(long id) {
        Long current = lagerId.getValue();
        if (current == null || current != id) {
            lagerId.setValue(id);
        }
    }

    public LiveData<List<Item>> getItems() {
        return filtered;
    }

    public void setQuery(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.getDefault());
        apply();
    }

    public void setStatusFilter(@Nullable ItemStatus status) {
        statusFilter = status;
        apply();
    }

    /** true, wenn das Lager ueberhaupt Artikel enthaelt (unabhaengig vom Filter). */
    public boolean hasItems() {
        return !alle.isEmpty();
    }

    private void apply() {
        List<Item> result = new ArrayList<>();
        for (Item item : alle) {
            if (statusFilter != null && item.status != statusFilter) {
                continue;
            }
            if (!query.isEmpty() && !matches(item)) {
                continue;
            }
            result.add(item);
        }
        filtered.setValue(result);
    }

    private boolean matches(Item item) {
        return contains(item.name) || contains(item.beschreibung) || contains(item.code)
                || contains(item.verliehenAn) || contains(item.notiz);
    }

    private boolean contains(@Nullable String value) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }
}
