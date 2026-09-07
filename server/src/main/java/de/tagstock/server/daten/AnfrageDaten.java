package de.tagstock.server.daten;

import de.tagstock.server.modell.Anfrage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Leih-Anfragen eines Teams. */
@Repository
public class AnfrageDaten {

    private final JdbcTemplate jdbc;

    public AnfrageDaten(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Anfrage> MAPPER = (zeile, nummer) -> {
        Anfrage anfrage = new Anfrage();
        anfrage.id = zeile.getString("id");
        anfrage.teamId = zeile.getString("team_id");
        anfrage.artikelId = zeile.getString("artikel_id");
        anfrage.artikelName = zeile.getString("artikel_name");
        anfrage.antragstellerId = zeile.getString("antragsteller_id");
        anfrage.antragstellerName = zeile.getString("antragsteller_name");
        anfrage.nachricht = zeile.getString("nachricht");
        anfrage.datumVon = (Long) zeile.getObject("datum_von");
        anfrage.datumBis = (Long) zeile.getObject("datum_bis");
        anfrage.status = zeile.getString("status");
        anfrage.bearbeiter = zeile.getString("bearbeiter");
        anfrage.antwort = zeile.getString("antwort");
        anfrage.erstelltAm = zeile.getLong("erstellt_am");
        anfrage.geaendertAm = zeile.getLong("geaendert_am");
        return anfrage;
    };

    public Anfrage anlegen(Anfrage anfrage) {
        anfrage.id = UUID.randomUUID().toString();
        anfrage.erstelltAm = System.currentTimeMillis();
        anfrage.geaendertAm = anfrage.erstelltAm;
        anfrage.status = Anfrage.OFFEN;
        jdbc.update("INSERT INTO anfragen (id, team_id, artikel_id, artikel_name,"
                        + " antragsteller_id, antragsteller_name, nachricht, datum_von, datum_bis,"
                        + " status, bearbeiter, antwort, erstellt_am, geaendert_am)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,NULL,NULL,?,?)",
                anfrage.id, anfrage.teamId, anfrage.artikelId, anfrage.artikelName,
                anfrage.antragstellerId, anfrage.antragstellerName, anfrage.nachricht,
                anfrage.datumVon, anfrage.datumBis, anfrage.status,
                anfrage.erstelltAm, anfrage.geaendertAm);
        return anfrage;
    }

    public Anfrage nachId(String teamId, String id) {
        List<Anfrage> treffer = jdbc.query("SELECT * FROM anfragen WHERE team_id = ? AND id = ?",
                MAPPER, teamId, id);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    public List<Anfrage> imTeam(String teamId, String status) {
        if (status == null || status.isEmpty()) {
            return jdbc.query("SELECT * FROM anfragen WHERE team_id = ?"
                    + " ORDER BY erstellt_am DESC LIMIT 200", MAPPER, teamId);
        }
        return jdbc.query("SELECT * FROM anfragen WHERE team_id = ? AND status = ?"
                + " ORDER BY erstellt_am DESC LIMIT 200", MAPPER, teamId, status);
    }

    public List<Anfrage> geaendertSeit(String teamId, long seit) {
        return jdbc.query("SELECT * FROM anfragen WHERE team_id = ? AND geaendert_am > ?"
                + " ORDER BY geaendert_am ASC", MAPPER, teamId, seit);
    }

    public void entscheiden(String teamId, String id, String status, String bearbeiter,
                            String antwort) {
        jdbc.update("UPDATE anfragen SET status = ?, bearbeiter = ?, antwort = ?,"
                        + " geaendert_am = ? WHERE team_id = ? AND id = ?",
                status, bearbeiter, antwort, System.currentTimeMillis(), teamId, id);
    }

    public int offene(String teamId) {
        Integer anzahl = jdbc.queryForObject(
                "SELECT COUNT(*) FROM anfragen WHERE team_id = ? AND status = ?",
                Integer.class, teamId, Anfrage.OFFEN);
        return anzahl == null ? 0 : anzahl;
    }
}
