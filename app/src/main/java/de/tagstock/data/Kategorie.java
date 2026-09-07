package de.tagstock.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Frei pflegbare Kategorie mit eigener Sortierreihenfolge. */
@Entity(tableName = "kategorien", indices = @Index(value = "name", unique = true))
public class Kategorie {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "serverId")
    public String serverId;

    @ColumnInfo(name = "teamId")
    public String teamId;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @ColumnInfo(name = "reihenfolge")
    public int reihenfolge;

    public Kategorie() {
    }

    @androidx.room.Ignore
    public Kategorie(@NonNull String name, int reihenfolge) {
        this.name = name;
        this.reihenfolge = reihenfolge;
    }
}
