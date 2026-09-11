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

    /** true, wenn dieser Eintrag selbst etwas aufnimmt - Box, Schublade, Regal. */
    public boolean istBehaelter;

    /** Was fuer ein Behaelter, z. B. "Box" oder "Schublade". Nur zur Anzeige. */
    public String behaelterArt;

    /** Kennung des Behaelters, in dem dieser Artikel liegt - null, wenn frei. */
    public String behaelterKennung;
    public long erstelltAm;
    public long geaendertAm;
    public boolean geloescht;

    /** Nur beim Hochladen belegt: die lokale Nummer aus der App. */
    public Long lokaleId;
}
