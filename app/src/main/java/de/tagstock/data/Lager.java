package de.tagstock.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** Ein Lager (Standort), in dem Artikel abgelegt werden. */
@Entity(tableName = "lager")
public class Lager {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @ColumnInfo(name = "beschreibung")
    public String beschreibung;

    @ColumnInfo(name = "ort")
    public String ort;

    @ColumnInfo(name = "erstelltAm")
    public long erstelltAm = System.currentTimeMillis();

    /**
     * Nur waehrend eines Imports belegt: die urspruengliche ID aus der Sicherung,
     * damit die Artikel dem neu angelegten Lager zugeordnet werden koennen.
     */
    @androidx.room.Ignore
    public long importId;

    public Lager() {
    }

    @Ignore
    public Lager(@NonNull String name, String beschreibung, String ort) {
        this.name = name;
        this.beschreibung = beschreibung;
        this.ort = ort;
    }
}
