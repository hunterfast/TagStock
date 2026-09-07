package de.tagstock.server.modell;

/** Ein Artikelbild. Der Inhalt liegt als Datei, hier stehen nur die Angaben. */
public class Bild {

    public String id;
    public String teamId;
    public String artikelId;
    /** Pfad unterhalb des Bilderordners, z. B. "<team>/<id>.jpg". */
    public String datei;
    public String typ;
    public long groesse;
    public String hochgeladenVon;
    public long erstelltAm;
}
