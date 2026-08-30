package de.tagstock.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ItemDao {

    /** Offene Ausleihen je Artikel; wird von allen Zustandsabfragen verwendet. */
    String VERLIEHEN = "(SELECT COALESCE(SUM(v.menge), 0) FROM verleih v"
            + " WHERE v.itemId = i.id AND v.zurueckAm IS NULL) AS verliehen";

    @Insert
    long insert(Item item);

    @Update
    void update(Item item);

    @Delete
    void delete(Item item);

    @Query("SELECT * FROM items WHERE id = :id")
    Item getById(long id);

    @Transaction
    @Query("SELECT i.*, " + VERLIEHEN + " FROM items i WHERE i.id = :id")
    LiveData<ItemWithState> observeState(long id);

    @Transaction
    @Query("SELECT i.*, " + VERLIEHEN + " FROM items i WHERE i.id = :id")
    ItemWithState getState(long id);

    @Transaction
    @Query("SELECT i.*, " + VERLIEHEN + " FROM items i WHERE i.lagerId = :lagerId"
            + " ORDER BY i.name COLLATE NOCASE ASC")
    LiveData<List<ItemWithState>> observeByLager(long lagerId);

    @Transaction
    @Query("SELECT i.*, " + VERLIEHEN + " FROM items i ORDER BY i.name COLLATE NOCASE ASC")
    LiveData<List<ItemWithState>> observeAll();

    @Transaction
    @Query("SELECT i.*, " + VERLIEHEN + " FROM items i WHERE i.lagerId = :lagerId")
    List<ItemWithState> getByLager(long lagerId);

    @Transaction
    @Query("SELECT i.*, " + VERLIEHEN + " FROM items i")
    List<ItemWithState> getAllStates();

    @Query("SELECT * FROM items")
    List<Item> getAll();

    @Query("DELETE FROM items")
    void deleteAll();
}
