package de.tagstock.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import de.tagstock.data.LagerWithCount;
import de.tagstock.data.Repository;

/** Liefert die Lagerliste inklusive Artikelzahlen. */
public class LagerListViewModel extends AndroidViewModel {

    private final LiveData<List<LagerWithCount>> lager;

    public LagerListViewModel(@NonNull Application application) {
        super(application);
        lager = Repository.getInstance(application).observeLagerWithCounts();
    }

    public LiveData<List<LagerWithCount>> getLager() {
        return lager;
    }
}
