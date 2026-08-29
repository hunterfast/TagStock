package de.tagstock.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LagerDao {

    @Insert
    long insert(Lager lager);

    @Update
    void update(Lager lager);

    @Delete
    void delete(Lager lager);

    @Query("SELECT * FROM lager ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<Lager>> observeAll();

    @Query("SELECT * FROM lager ORDER BY name COLLATE NOCASE ASC")
    List<Lager> getAll();

    @Query("SELECT * FROM lager WHERE id = :id")
    LiveData<Lager> observeById(long id);

    @Query("SELECT * FROM lager WHERE id = :id")
    Lager getById(long id);

    @Query("SELECT l.*,"
            + " (SELECT COUNT(*) FROM items i WHERE i.lagerId = l.id) AS gesamt,"
            + " (SELECT COUNT(*) FROM items i WHERE i.lagerId = l.id AND i.status = 'VORHANDEN') AS vorhanden,"
            + " (SELECT COUNT(*) FROM items i WHERE i.lagerId = l.id AND i.status = 'VERLIEHEN') AS verliehen,"
            + " (SELECT COUNT(*) FROM items i WHERE i.lagerId = l.id AND i.status = 'VERLOREN') AS verloren"
            + " FROM lager l ORDER BY l.name COLLATE NOCASE ASC")
    LiveData<List<LagerWithCount>> observeAllWithCounts();
}
