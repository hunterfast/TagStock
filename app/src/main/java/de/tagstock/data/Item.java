package de.tagstock.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Ein Artikel, der genau einem Lager zugeordnet ist. */
@Entity(
        tableName = "items",
        foreignKeys = @ForeignKey(
                entity = Lager.class,
                parentColumns = "id",
                childColumns = "lagerId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("lagerId"), @Index("code")})
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

    /** Barcode-, QR- oder NFC-Kennung. Null, wenn der Artikel nur manuell gepflegt wird. */
    @ColumnInfo(name = "code")
    public String code;

    @NonNull
    @ColumnInfo(name = "codeType")
    public CodeType codeType = CodeType.KEINER;

    @ColumnInfo(name = "menge")
    public int menge = 1;

    @NonNull
    @ColumnInfo(name = "status")
    public ItemStatus status = ItemStatus.VORHANDEN;

    /** Nur gesetzt, wenn der Status VERLIEHEN ist. */
    @ColumnInfo(name = "verliehenAn")
    public String verliehenAn;

    @ColumnInfo(name = "verliehenSeit")
    public Long verliehenSeit;

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
        copy.code = code;
        copy.codeType = codeType;
        copy.menge = menge;
        copy.status = status;
        copy.verliehenAn = verliehenAn;
        copy.verliehenSeit = verliehenSeit;
        copy.notiz = notiz;
        copy.erstelltAm = erstelltAm;
        copy.geaendertAm = geaendertAm;
        return copy;
    }
}
