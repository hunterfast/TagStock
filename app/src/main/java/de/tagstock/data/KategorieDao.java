package de.tagstock.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface KategorieDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Kategorie kategorie);

    @Update
    void update(Kategorie kategorie);

    @Delete
    void delete(Kategorie kategorie);

    @Query("SELECT * FROM kategorien ORDER BY reihenfolge ASC, name COLLATE NOCASE ASC")
    LiveData<List<Kategorie>> beobachteAlle();

    @Query("SELECT * FROM kategorien ORDER BY reihenfolge ASC, name COLLATE NOCASE ASC")
    List<Kategorie> alle();

    @Query("SELECT COUNT(*) FROM kategorien")
    int anzahl();

    @Query("DELETE FROM kategorien")
    void alleLoeschen();
}
