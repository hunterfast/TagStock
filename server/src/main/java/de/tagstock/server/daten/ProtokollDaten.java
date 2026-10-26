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
        // Eingegangen ist nicht dasselbe wie geschehen: Ein Handy, das eine Woche
        // ohne Netz war, liefert Eintraege mit altem Zeitpunkt nach.
        jdbc.update("INSERT OR IGNORE INTO protokoll (id, team_id, artikel_id, artikel_name,"
                        + " aktion, alter_wert, neuer_wert, nutzer, zeitpunkt, eingegangen)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?)",
                eintrag.id, eintrag.teamId, eintrag.artikelId, eintrag.artikelName,
                eintrag.aktion, eintrag.alterWert, eintrag.neuerWert, eintrag.nutzer,
                eintrag.zeitpunkt, System.currentTimeMillis());
        return eintrag;
    }

    public List<Protokoll> zuArtikel(String teamId, String artikelId) {
        return jdbc.query("SELECT * FROM protokoll WHERE team_id = ? AND artikel_id = ?"
                + " ORDER BY zeitpunkt DESC LIMIT 100", MAPPER, teamId, artikelId);
    }

    /**
     * Was seit dem letzten Abgleich eines Geraets eingegangen ist - nach dem
     * Eingang, nicht nach dem Zeitpunkt: Sonst sahen die anderen Geraete nie,
     * was ein Handy nachreicht, das eine Weile ohne Netz war. Und ohne Grenze:
     * Das Geraet merkt sich danach den Stand, alles darueber hinaus waere fuer
     * immer verloren. Aeltere Eintraege kennen ihren Eingang nicht; fuer sie
     * steht der Zeitpunkt.
     */
    public List<Protokoll> seit(String teamId, long seit) {
        return jdbc.query("SELECT * FROM protokoll WHERE team_id = ?"
                + " AND COALESCE(eingegangen, zeitpunkt) > ?"
                + " ORDER BY COALESCE(eingegangen, zeitpunkt) ASC", MAPPER, teamId, seit);
    }
}
