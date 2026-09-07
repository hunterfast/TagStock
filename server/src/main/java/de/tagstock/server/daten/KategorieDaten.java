package de.tagstock.server.daten;

import de.tagstock.server.modell.Kategorie;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Kategorien eines Teams. */
@Repository
public class KategorieDaten {

    private static final String[] STANDARD = {
            "Elektronik", "Werkzeug", "Büromaterial", "Lager",
            "Verbrauchsmaterial", "Möbel", "3D Drucker", "Sonstiges"};

    private final JdbcTemplate jdbc;

    public KategorieDaten(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Kategorie> MAPPER = (zeile, nummer) -> {
        Kategorie kategorie = new Kategorie();
        kategorie.id = zeile.getString("id");
        kategorie.teamId = zeile.getString("team_id");
        kategorie.name = zeile.getString("name");
        kategorie.reihenfolge = zeile.getInt("reihenfolge");
        kategorie.geaendertAm = zeile.getLong("geaendert_am");
        kategorie.geloescht = zeile.getInt("geloescht") == 1;
        return kategorie;
    };

    public List<Kategorie> imTeam(String teamId) {
        return jdbc.query("SELECT * FROM kategorien WHERE team_id = ? AND geloescht = 0"
                + " ORDER BY reihenfolge ASC, name COLLATE NOCASE ASC", MAPPER, teamId);
    }

    public List<Kategorie> geaendertSeit(String teamId, long seit) {
        return jdbc.query("SELECT * FROM kategorien WHERE team_id = ? AND geaendert_am > ?",
                MAPPER, teamId, seit);
    }

    public Kategorie anlegen(String teamId, String name, int reihenfolge) {
        Kategorie kategorie = new Kategorie();
        kategorie.id = UUID.randomUUID().toString();
        kategorie.teamId = teamId;
        kategorie.name = name;
        kategorie.reihenfolge = reihenfolge;
        kategorie.geaendertAm = System.currentTimeMillis();
        jdbc.update("INSERT INTO kategorien (id, team_id, name, reihenfolge, geaendert_am,"
                        + " geloescht) VALUES (?, ?, ?, ?, ?, 0)",
                kategorie.id, teamId, name, reihenfolge, kategorie.geaendertAm);
        return kategorie;
    }

    public void aktualisieren(Kategorie kategorie) {
        kategorie.geaendertAm = System.currentTimeMillis();
        jdbc.update("UPDATE kategorien SET name = ?, reihenfolge = ?, geaendert_am = ?,"
                        + " geloescht = ? WHERE team_id = ? AND id = ?",
                kategorie.name, kategorie.reihenfolge, kategorie.geaendertAm,
                kategorie.geloescht ? 1 : 0, kategorie.teamId, kategorie.id);
    }

    public void loeschen(String teamId, String id) {
        jdbc.update("UPDATE kategorien SET geloescht = 1, geaendert_am = ?"
                + " WHERE team_id = ? AND id = ?", System.currentTimeMillis(), teamId, id);
    }

    /** Legt die Standardkategorien an, sobald ein Team entsteht. */
    public void standardAnlegen(String teamId) {
        for (int i = 0; i < STANDARD.length; i++) {
            anlegen(teamId, STANDARD[i], i);
        }
    }
}
