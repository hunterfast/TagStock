package de.tagstock.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Eintrag im Aenderungsprotokoll eines Artikels. */
@Entity(
        tableName = "protokoll",
        foreignKeys = @ForeignKey(
                entity = Artikel.class,
                parentColumns = "id",
                childColumns = "artikelId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("artikelId"))
public class Protokoll {

    /** Bekannte Aktionen - der Text wandert unveraendert in die Anzeige. */
    public static final String ANGELEGT = "Artikel erstellt";
    public static final String STATUS = "Status geändert";
    public static final String STANDORT = "Standort geändert";
    public static final String LAGERORT = "Lagerort geändert";
    public static final String NAME = "Name geändert";
    public static final String GESCANNT = "Gescannt";
    public static final String VERLIEHEN = "Verliehen";
    public static final String ZURUECK = "Zurückgenommen";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "serverId")
    public String serverId;

    @ColumnInfo(name = "artikelId")
    public long artikelId;

    @NonNull
    @ColumnInfo(name = "artikelName")
    public String artikelName = "";

    @NonNull
    @ColumnInfo(name = "aktion")
    public String aktion = "";

    @ColumnInfo(name = "alterWert")
    public String alterWert;

    @ColumnInfo(name = "neuerWert")
    public String neuerWert;

    @ColumnInfo(name = "nutzer")
    public String nutzer;

    @ColumnInfo(name = "zeitpunkt")
    public long zeitpunkt = System.currentTimeMillis();

    @ColumnInfo(name = "offen")
    public boolean offen = true;

    public Protokoll() {
    }

    @androidx.room.Ignore
    public static Protokoll fuer(Artikel artikel, String aktion, String alterWert,
                                 String neuerWert, String nutzer) {
        Protokoll eintrag = new Protokoll();
        eintrag.artikelId = artikel.id;
        eintrag.artikelName = artikel.name;
        eintrag.aktion = aktion;
        eintrag.alterWert = alterWert;
        eintrag.neuerWert = neuerWert;
        eintrag.nutzer = nutzer;
        return eintrag;
    }

    public boolean istStandortWechsel() {
        return STANDORT.equals(aktion) || LAGERORT.equals(aktion);
    }
}
