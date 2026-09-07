package de.tagstock.server.modell;

/** Leih-Anfrage zu einem Artikel. */
public class Anfrage {

    public static final String OFFEN = "offen";
    public static final String GENEHMIGT = "genehmigt";
    public static final String ABGELEHNT = "abgelehnt";

    public String id;
    public String teamId;
    public String artikelId;
    public String artikelName;
    public String antragstellerId;
    public String antragstellerName;
    public String nachricht;
    public Long datumVon;
    public Long datumBis;
    public String status = OFFEN;
    public String bearbeiter;
    public String antwort;
    public long erstelltAm;
    public long geaendertAm;
}
