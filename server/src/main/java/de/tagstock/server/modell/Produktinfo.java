package de.tagstock.server.modell;

/** Was ein Nachschlagedienst zu einer GTIN weiss. */
public class Produktinfo {

    public String gtin;
    public boolean gefunden;
    public String name;
    public String marke;
    public String kategorie;
    /** Woher die Angaben stammen, etwa "opengtindb". */
    public String quelle;
    public long geholtAm;
    /** true, wenn die Antwort aus dem Zwischenspeicher kam. */
    public boolean ausCache;
}
