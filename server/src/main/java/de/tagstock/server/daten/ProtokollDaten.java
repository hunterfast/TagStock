package de.tagstock.server.daten;

import de.tagstock.server.modell.Protokoll;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Aenderungsprotokoll eines Teams. */
@Repository
public class ProtokollDaten {

    private final JdbcTemplate jdbc;

    public ProtokollDaten(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Protokoll> MAPPER = (zeile, nummer) -> {
        Protokoll eintrag = new Protokoll();
        eintrag.id = zeile.getString("id");
        eintrag.teamId = zeile.getString("team_id");
        eintrag.artikelId = zeile.getString("artikel_id");
        eintrag.artikelName = zeile.getString("artikel_name");
        eintrag.aktion = zeile.getString("aktion");
        eintrag.alterWert = zeile.getString("alter_wert");
        eintrag.neuerWert = zeile.getString("neuer_wert");
        eintrag.nutzer = zeile.getString("nutzer");
        eintrag.zeitpunkt = zeile.getLong("zeitpunkt");
        return eintrag;
    };

    public Protokoll anlegen(Protokoll eintrag) {
        eintrag.id = eintrag.id == null ? UUID.randomUUID().toString() : eintrag.id;
        if (eintrag.zeitpunkt == 0) {
            eintrag.zeitpunkt = System.currentTimeMillis();
        }
        jdbc.update("INSERT OR IGNORE INTO protokoll (id, team_id, artikel_id, artikel_name,"
                        + " aktion, alter_wert, neuer_wert, nutzer, zeitpunkt)"
                        + " VALUES (?,?,?,?,?,?,?,?,?)",
                eintrag.id, eintrag.teamId, eintrag.artikelId, eintrag.artikelName,
                eintrag.aktion, eintrag.alterWert, eintrag.neuerWert, eintrag.nutzer,
                eintrag.zeitpunkt);
        return eintrag;
    }

    public List<Protokoll> zuArtikel(String teamId, String artikelId) {
        return jdbc.query("SELECT * FROM protokoll WHERE team_id = ? AND artikel_id = ?"
                + " ORDER BY zeitpunkt DESC LIMIT 100", MAPPER, teamId, artikelId);
    }

    public List<Protokoll> seit(String teamId, long seit) {
        return jdbc.query("SELECT * FROM protokoll WHERE team_id = ? AND zeitpunkt > ?"
                + " ORDER BY zeitpunkt ASC LIMIT 500", MAPPER, teamId, seit);
    }
}
