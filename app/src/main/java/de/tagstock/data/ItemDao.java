package de.tagstock.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ItemDao {

    @Insert
    long insert(Item item);

    @Update
    void update(Item item);

    @Delete
    void delete(Item item);

    @Query("SELECT * FROM items WHERE id = :id")
    Item getById(long id);

    @Query("SELECT * FROM items WHERE id = :id")
    LiveData<Item> observeById(long id);

    @Query("SELECT * FROM items WHERE lagerId = :lagerId ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<Item>> observeByLager(long lagerId);

    @Query("SELECT * FROM items ORDER BY geaendertAm DESC")
    LiveData<List<Item>> observeAll();

    /** Suche ueber alle Lager hinweg (Name, Beschreibung, Code). */
    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%'"
            + " OR beschreibung LIKE '%' || :query || '%'"
            + " OR code LIKE '%' || :query || '%'"
            + " ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<Item>> search(String query);

    /** Erster Treffer zu einem gescannten Code, unabhaengig vom Lager. */
    @Query("SELECT * FROM items WHERE code = :code LIMIT 1")
    Item findByCode(String code);

    @Query("SELECT * FROM items WHERE code = :code AND lagerId = :lagerId LIMIT 1")
    Item findByCodeInLager(String code, long lagerId);

    @Query("SELECT COUNT(*) FROM items WHERE lagerId = :lagerId")
    int countInLager(long lagerId);
}
