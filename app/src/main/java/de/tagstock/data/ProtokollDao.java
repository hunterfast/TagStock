package de.tagstock.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

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

    @Query("UPDATE protokoll SET offen = 0 WHERE id IN (:ids)")
    void alsGesendetMarkieren(java.util.List<Long> ids);

    @Query("DELETE FROM protokoll")
    void alleLoeschen();

    @Update
    void update(Protokoll eintrag);

    @Query("SELECT * FROM protokoll WHERE serverId = :serverId LIMIT 1")
    Protokoll nachServerId(String serverId);

    /**
     * Ein eigener Eintrag, den der Server gerade zurueckschickt: gleicher
     * Artikel, gleiche Aktion, gleicher Zeitpunkt - aber hier noch ohne
     * Server-Kennung, weil er von hier stammt.
     */
    @Query("SELECT * FROM protokoll WHERE serverId IS NULL AND artikelId = :artikelId"
            + " AND aktion = :aktion AND zeitpunkt = :zeitpunkt LIMIT 1")
    Protokoll eigenerOhneKennung(long artikelId, String aktion, long zeitpunkt);
}
