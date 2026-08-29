package de.tagstock.data;

import androidx.room.Embedded;

/** Lager inklusive der Artikelzahlen je Status fuer die Uebersichtsliste. */
public class LagerWithCount {

    @Embedded
    public Lager lager;

    public int gesamt;
    public int vorhanden;
    public int verliehen;
    public int verloren;
}
