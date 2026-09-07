package de.tagstock.server.modell;

/** Ein Artikel im Bestand eines Teams - Felder wie in der App. */
public class Artikel {

    public String id;
    public String teamId;
    public String rfidUid;
    public String name;
    public String beschreibung;
    public String kategorie;
    public String standort;
    public String lagerort;
    public String bildUrl;
    public String status = "vorhanden";
    public String verliehenAn;
    public Long rueckgabeDatum;
    public Long zuletztGescannt;
    public String scanWarnung = "1j";
    public long erstelltAm;
    public long geaendertAm;
    public boolean geloescht;

    /** Nur beim Hochladen belegt: die lokale Nummer aus der App. */
    public Long lokaleId;
}
