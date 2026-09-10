package de.tagstock.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ArtikelDao {

    @Insert
    long insert(Artikel artikel);

    @Update
    void update(Artikel artikel);

    @Delete
    void delete(Artikel artikel);

    @Query("SELECT * FROM artikel ORDER BY geaendertAm DESC")
    LiveData<List<Artikel>> beobachteAlle();

    @Query("SELECT * FROM artikel ORDER BY geaendertAm DESC")
    List<Artikel> alle();

    @Query("SELECT * FROM artikel WHERE id = :id")
    LiveData<Artikel> beobachte(long id);

    @Query("SELECT * FROM artikel WHERE id = :id")
    Artikel nachId(long id);

    @Query("SELECT * FROM artikel WHERE rfidUid = :wert LIMIT 1")
    Artikel nachKennung(String wert);

    @Query("SELECT * FROM artikel WHERE serverId = :serverId LIMIT 1")
    Artikel nachServerId(String serverId);

    /** Alle Standorte, die tatsaechlich vergeben sind. */
    @Query("SELECT DISTINCT standort FROM artikel WHERE standort IS NOT NULL AND standort != ''"
            + " ORDER BY standort COLLATE NOCASE ASC")
    LiveData<List<String>> beobachteStandorte();

    @Query("SELECT DISTINCT kategorie FROM artikel WHERE kategorie IS NOT NULL AND kategorie != ''"
            + " ORDER BY kategorie COLLATE NOCASE ASC")
    LiveData<List<String>> beobachteKategorien();

    @Query("SELECT * FROM artikel WHERE offen = 1")
    List<Artikel> offene();

    @Query("SELECT COUNT(*) FROM artikel WHERE offen = 1")
    int anzahlOffene();

    /** Artikel mit einer Aufnahme, die noch auf den Server gehoert. */
    @Query("SELECT * FROM artikel WHERE fotoPfad IS NOT NULL AND fotoPfad != ''"
            + " AND (bildUrl IS NULL OR bildUrl = '') AND serverId IS NOT NULL")
    List<Artikel> mitOffenemBild();

    @Query("DELETE FROM artikel")
    void alleLoeschen();
}
