package de.tagstock.data;

import androidx.room.Embedded;

/** Lager samt Stueckzahlen je Zustand fuer die Uebersichtsliste. */
public class LagerWithCount {

    @Embedded
    public Lager lager;

    /** Summe aller Stueckzahlen im Lager. */
    public int gesamt;

    /** Summe der offenen Ausleihen. */
    public int verliehen;

    /** Summe der als verloren gemeldeten Stuecke. */
    public int verloren;

    /** Anzahl der Artikelpositionen (nicht Stueckzahlen). */
    public int artikel;

    public int vorhanden() {
        return Math.max(0, gesamt - verliehen - verloren);
    }
}
