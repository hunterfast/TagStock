package de.tagstock.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface VerleihDao {

    @Insert
    long insert(Verleih verleih);

    @Update
    void update(Verleih verleih);

    @Delete
    void delete(Verleih verleih);

    /** Offene Vorgaenge zuerst, danach die Historie nach Datum absteigend. */
    @Query("SELECT * FROM verleih WHERE itemId = :itemId"
            + " ORDER BY (zurueckAm IS NOT NULL) ASC, ausgeliehenAm DESC")
    LiveData<List<Verleih>> observeByItem(long itemId);

    @Query("SELECT * FROM verleih WHERE itemId = :itemId AND zurueckAm IS NULL"
            + " ORDER BY ausgeliehenAm ASC")
    List<Verleih> getOffene(long itemId);

    @Query("SELECT COALESCE(SUM(menge), 0) FROM verleih WHERE itemId = :itemId AND zurueckAm IS NULL")
    int offeneMenge(long itemId);

    @Query("SELECT * FROM verleih")
    List<Verleih> getAll();

    /** Alle offenen Ausleihen, neueste zuerst - fuer die Gesamtuebersicht. */
    @Query("SELECT * FROM verleih WHERE zurueckAm IS NULL ORDER BY ausgeliehenAm DESC")
    LiveData<List<Verleih>> observeOffene();
}
