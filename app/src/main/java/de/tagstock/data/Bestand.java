package de.tagstock.data;

import java.util.ArrayList;
import java.util.List;

/** Kompletter Datenbestand - Grundlage fuer Sicherung und Wiederherstellung. */
public class Bestand {

    public final List<Lager> lager;
    public final List<Item> items;
    public final List<Code> codes;
    public final List<Verleih> verleihe;

    public Bestand() {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public Bestand(List<Lager> lager, List<Item> items, List<Code> codes, List<Verleih> verleihe) {
        this.lager = lager;
        this.items = items;
        this.codes = codes;
        this.verleihe = verleihe;
    }

    public boolean istLeer() {
        return lager.isEmpty() && items.isEmpty();
    }
}
