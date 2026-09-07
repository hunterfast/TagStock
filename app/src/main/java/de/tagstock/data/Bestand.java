package de.tagstock.data;

import java.util.ArrayList;
import java.util.List;

/** Kompletter Datenbestand - Grundlage fuer Sicherung und Wiederherstellung. */
public class Bestand {

    public final List<Artikel> artikel;
    public final List<Kategorie> kategorien;
    public final List<Protokoll> protokoll;

    public Bestand() {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public Bestand(List<Artikel> artikel, List<Kategorie> kategorien, List<Protokoll> protokoll) {
        this.artikel = artikel;
        this.kategorien = kategorien;
        this.protokoll = protokoll;
    }

    public boolean istLeer() {
        return artikel.isEmpty() && kategorien.isEmpty();
    }
}
