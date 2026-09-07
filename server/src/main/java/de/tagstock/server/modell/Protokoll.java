package de.tagstock.server.modell;

/** Eintrag im Aenderungsprotokoll. */
public class Protokoll {

    public String id;
    public String teamId;
    public String artikelId;
    public String artikelName;
    public String aktion;
    public String alterWert;
    public String neuerWert;
    public String nutzer;
    public long zeitpunkt;
}
