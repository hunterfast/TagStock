package de.tagstock.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein Artikel, der genau einem Lager zugeordnet ist.
 *
 * <p>{@code menge} ist der Gesamtbestand. Wie viele Stuecke davon verliehen sind,
 * ergibt sich aus den offenen Eintraegen in {@link Verleih}; {@code mengeVerloren}
 * haelt fest, wie viele Stuecke als verloren gemeldet wurden. Vorhanden ist der Rest.
 */
@Entity(
        tableName = "items",
        foreignKeys = @ForeignKey(
                entity = Lager.class,
                parentColumns = "id",
                childColumns = "lagerId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("lagerId"))
public class Item {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "lagerId")
    public long lagerId;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @ColumnInfo(name = "beschreibung")
    public String beschreibung;

    /** Gesamtbestand dieses Artikels. */
    @ColumnInfo(name = "menge")
    public int menge = 1;

    /** Davon als verloren gemeldet. */
    @ColumnInfo(name = "mengeVerloren")
    public int mengeVerloren;

    /** Dateiname des Fotos im App-Verzeichnis, oder null. */
    @ColumnInfo(name = "fotoPfad")
    public String fotoPfad;

    @ColumnInfo(name = "notiz")
    public String notiz;

    @ColumnInfo(name = "erstelltAm")
    public long erstelltAm = System.currentTimeMillis();

    @ColumnInfo(name = "geaendertAm")
    public long geaendertAm = System.currentTimeMillis();

    /** Flache Kopie - noetig, damit die Liste Aenderungen zuverlaessig erkennt. */
    public Item copy() {
        Item copy = new Item();
        copy.id = id;
        copy.lagerId = lagerId;
        copy.name = name;
        copy.beschreibung = beschreibung;
        copy.menge = menge;
        copy.mengeVerloren = mengeVerloren;
        copy.fotoPfad = fotoPfad;
        copy.notiz = notiz;
        copy.erstelltAm = erstelltAm;
        copy.geaendertAm = geaendertAm;
        return copy;
    }
}
