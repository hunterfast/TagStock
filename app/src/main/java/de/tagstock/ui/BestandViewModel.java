package de.tagstock.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.Repository;

/** Bestandsliste mit Suche und Filtern nach Status, Kategorie und Standort. */
public class BestandViewModel extends AndroidViewModel {

    public static final String ALLE = "alle";

    private final Repository repository;
    private final MediatorLiveData<List<Artikel>> gefiltert = new MediatorLiveData<>();
    private final MediatorLiveData<List<Artikel>> alle = new MediatorLiveData<>();

    private List<Artikel> bestand = Collections.emptyList();
    private String suche = "";
    @Nullable
    private ArtikelStatus status;
    private String kategorie = ALLE;
    private String standort = ALLE;

    public BestandViewModel(@NonNull Application application) {
        super(application);
        repository = Repository.getInstance(application);
        LiveData<List<Artikel>> quelle = repository.beobachteArtikel();
        gefiltert.addSource(quelle, artikel -> {
            bestand = artikel == null ? Collections.emptyList() : artikel;
            alle.setValue(bestand);
            anwenden();
        });
    }

    public LiveData<List<Artikel>> getGefiltert() {
        return gefiltert;
    }

    /** Ungefilterte Liste - fuer Zahlen, Banner und Auswahllisten. */
    public LiveData<List<Artikel>> getAlle() {
        return alle;
    }

    public LiveData<List<String>> getStandorte() {
        return repository.beobachteStandorte();
    }

    public void setSuche(String wert) {
        suche = wert == null ? "" : wert.trim().toLowerCase(Locale.getDefault());
        anwenden();
    }

    public void setStatus(@Nullable ArtikelStatus wert) {
        status = wert;
        anwenden();
    }

    @Nullable
    public ArtikelStatus getStatus() {
        return status;
    }

    public void setKategorie(String wert) {
        kategorie = wert == null ? ALLE : wert;
        anwenden();
    }

    public String getKategorie() {
        return kategorie;
    }

    public void setStandort(String wert) {
        standort = wert == null ? ALLE : wert;
        anwenden();
    }

    public String getStandort() {
        return standort;
    }

    public boolean hatBestand() {
        return !bestand.isEmpty();
    }

    /** Kategorien, die im Bestand tatsaechlich vorkommen. */
    public List<String> verwendeteKategorien() {
        return verschiedene(true);
    }

    public List<String> verwendeteStandorte() {
        return verschiedene(false);
    }

    private List<String> verschiedene(boolean kategorien) {
        List<String> werte = new ArrayList<>();
        for (Artikel artikel : bestand) {
            String wert = kategorien ? artikel.kategorie : artikel.standort;
            if (wert != null && !wert.trim().isEmpty() && !werte.contains(wert)) {
                werte.add(wert);
            }
        }
        Collections.sort(werte, String.CASE_INSENSITIVE_ORDER);
        return werte;
    }

    private void anwenden() {
        gefiltert.setValue(filtern(bestand, suche, status, kategorie, standort));
    }

    /** Filterlogik - ausgelagert, damit sie sich pruefen laesst. */
    public static List<Artikel> filtern(List<Artikel> quelle, String suche,
                                        @Nullable ArtikelStatus status,
                                        String kategorie, String standort) {
        List<Artikel> ergebnis = new ArrayList<>();
        for (Artikel artikel : quelle) {
            if (status != null && artikel.status != status) {
                continue;
            }
            if (!ALLE.equals(kategorie) && !gleich(artikel.kategorie, kategorie)) {
                continue;
            }
            if (!ALLE.equals(standort) && !gleich(artikel.standort, standort)) {
                continue;
            }
            if (!suche.isEmpty() && !passt(artikel, suche)) {
                continue;
            }
            ergebnis.add(artikel);
        }
        return ergebnis;
    }

    public static boolean passt(Artikel artikel, String suche) {
        return enthaelt(artikel.name, suche)
                || enthaelt(artikel.rfidUid, suche)
                || enthaelt(artikel.standort, suche)
                || enthaelt(artikel.lagerort, suche)
                || enthaelt(artikel.kategorie, suche)
                || enthaelt(artikel.beschreibung, suche)
                || enthaelt(artikel.verliehenAn, suche);
    }

    private static boolean enthaelt(@Nullable String wert, String suche) {
        return wert != null && wert.toLowerCase(Locale.getDefault()).contains(suche);
    }

    private static boolean gleich(@Nullable String wert, String erwartet) {
        return wert != null && wert.equals(erwartet);
    }
}
