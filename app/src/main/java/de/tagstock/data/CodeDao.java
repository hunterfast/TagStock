package de.tagstock.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CodeDao {

    /** Wirft bei einem bereits vergebenen Wert eine SQLiteConstraintException. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Code code);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAllIgnore(List<Code> codes);

    @Delete
    void delete(Code code);

    @Query("SELECT * FROM codes WHERE itemId = :itemId ORDER BY erfasstAm ASC")
    LiveData<List<Code>> observeByItem(long itemId);

    @Query("SELECT * FROM codes WHERE itemId = :itemId ORDER BY erfasstAm ASC")
    List<Code> getByItem(long itemId);

    @Query("SELECT * FROM codes WHERE wert = :wert LIMIT 1")
    Code findByWert(String wert);

    @Query("SELECT * FROM codes")
    List<Code> getAll();
}
