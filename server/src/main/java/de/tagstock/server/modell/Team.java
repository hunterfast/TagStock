package de.tagstock.server.modell;

/** Ein Team teilt sich einen Bestand. */
public class Team {

    public String id;
    public String name;
    public String beschreibung;
    public String erstellerId;
    public long erstelltAm;
    /** Rolle des anfragenden Kontos - nur in Antworten gefuellt. */
    public String rolle;
    /** Nur in der Uebersicht der Betreuung gefuellt. */
    public Integer anzahlArtikel;
    public Integer anzahlMitglieder;
}
