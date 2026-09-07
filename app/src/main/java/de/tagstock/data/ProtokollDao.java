package de.tagstock.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ProtokollDao {

    @Insert
    long insert(Protokoll eintrag);

    @Query("SELECT * FROM protokoll WHERE artikelId = :artikelId ORDER BY zeitpunkt DESC LIMIT 50")
    LiveData<List<Protokoll>> beobachteFuerArtikel(long artikelId);

    @Query("SELECT * FROM protokoll ORDER BY zeitpunkt DESC LIMIT 200")
    LiveData<List<Protokoll>> beobachteAlle();

    @Query("SELECT * FROM protokoll")
    List<Protokoll> alle();

    @Query("SELECT * FROM protokoll WHERE offen = 1")
    List<Protokoll> offene();

    @Query("DELETE FROM protokoll")
    void alleLoeschen();
}
