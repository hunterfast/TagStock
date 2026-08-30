package de.tagstock.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ein Code (Barcode, QR-Code oder NFC-Tag), der auf einen Artikel zeigt.
 * Ein Artikel kann mehrere Codes haben - etwa den Herstellerbarcode und
 * einen selbst aufgeklebten NFC-Tag. Der Wert ist geraeteweit eindeutig,
 * damit ein Scan immer genau einen Artikel trifft.
 */
@Entity(
        tableName = "codes",
        foreignKeys = @ForeignKey(
                entity = Item.class,
                parentColumns = "id",
                childColumns = "itemId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "wert", unique = true), @Index("itemId")})
public class Code {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "itemId")
    public long itemId;

    @NonNull
    @ColumnInfo(name = "wert")
    public String wert = "";

    @NonNull
    @ColumnInfo(name = "typ")
    public CodeType typ = CodeType.MANUELL;

    @ColumnInfo(name = "erfasstAm")
    public long erfasstAm = System.currentTimeMillis();

    public Code() {
    }

    @androidx.room.Ignore
    public Code(long itemId, @NonNull String wert, @NonNull CodeType typ) {
        this.itemId = itemId;
        this.wert = wert;
        this.typ = typ;
    }
}
