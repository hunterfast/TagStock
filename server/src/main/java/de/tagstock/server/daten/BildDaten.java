package de.tagstock.server.daten;

import de.tagstock.server.modell.Bild;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Angaben zu den Artikelbildern. Der Inhalt liegt im Bilderordner. */
@Repository
public class BildDaten {

    private final JdbcTemplate jdbc;

    public BildDaten(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Bild> MAPPER = (zeile, nummer) -> {
        Bild bild = new Bild();
        bild.id = zeile.getString("id");
        bild.teamId = zeile.getString("team_id");
        bild.artikelId = zeile.getString("artikel_id");
        bild.datei = zeile.getString("datei");
        bild.typ = zeile.getString("typ");
        bild.groesse = zeile.getLong("groesse");
        bild.hochgeladenVon = zeile.getString("hochgeladen_von");
        bild.erstelltAm = zeile.getLong("erstellt_am");
        return bild;
    };

    public Bild nachId(String teamId, String id) {
        List<Bild> treffer = jdbc.query("SELECT * FROM bilder WHERE team_id = ? AND id = ?",
                MAPPER, teamId, id);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    public List<Bild> zuArtikel(String teamId, String artikelId) {
        return jdbc.query("SELECT * FROM bilder WHERE team_id = ? AND artikel_id = ?"
                + " ORDER BY erstellt_am DESC", MAPPER, teamId, artikelId);
    }

    public Bild anlegen(Bild bild) {
        bild.id = bild.id == null ? UUID.randomUUID().toString() : bild.id;
        bild.erstelltAm = bild.erstelltAm == 0 ? System.currentTimeMillis() : bild.erstelltAm;
        jdbc.update("INSERT INTO bilder (id, team_id, artikel_id, datei, typ, groesse,"
                        + " hochgeladen_von, erstellt_am) VALUES (?,?,?,?,?,?,?,?)",
                bild.id, bild.teamId, bild.artikelId, bild.datei, bild.typ, bild.groesse,
                bild.hochgeladenVon, bild.erstelltAm);
        return bild;
    }

    public void loeschen(String teamId, String id) {
        jdbc.update("DELETE FROM bilder WHERE team_id = ? AND id = ?", teamId, id);
    }
}
