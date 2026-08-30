package de.tagstock.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein Ausleihvorgang. Offene Vorgaenge ({@code zurueckAm == null}) bestimmen,
 * wie viele Stuecke eines Artikels gerade verliehen sind; abgeschlossene
 * Vorgaenge bleiben als Historie stehen.
 */
@Entity(
        tableName = "verleih",
        foreignKeys = @ForeignKey(
                entity = Item.class,
                parentColumns = "id",
                childColumns = "itemId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("itemId"))
public class Verleih {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "itemId")
    public long itemId;

    @NonNull
    @ColumnInfo(name = "person")
    public String person = "";

    @ColumnInfo(name = "menge")
    public int menge = 1;

    @ColumnInfo(name = "ausgeliehenAm")
    public long ausgeliehenAm = System.currentTimeMillis();

    /** null, solange der Artikel nicht zurueck ist. */
    @ColumnInfo(name = "zurueckAm")
    public Long zurueckAm;

    @ColumnInfo(name = "notiz")
    public String notiz;

    public boolean istOffen() {
        return zurueckAm == null;
    }
}
