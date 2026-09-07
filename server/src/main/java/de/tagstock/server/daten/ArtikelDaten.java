package de.tagstock.server.daten;

import de.tagstock.server.modell.Artikel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Artikel eines Teams. */
@Repository
public class ArtikelDaten {

    private final JdbcTemplate jdbc;

    public ArtikelDaten(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Artikel> MAPPER = (zeile, nummer) -> {
        Artikel artikel = new Artikel();
        artikel.id = zeile.getString("id");
        artikel.teamId = zeile.getString("team_id");
        artikel.rfidUid = zeile.getString("rfid_uid");
        artikel.name = zeile.getString("name");
        artikel.beschreibung = zeile.getString("beschreibung");
        artikel.kategorie = zeile.getString("kategorie");
        artikel.standort = zeile.getString("standort");
        artikel.lagerort = zeile.getString("lagerort");
        artikel.bildUrl = zeile.getString("bild_url");
        artikel.status = zeile.getString("status");
        artikel.verliehenAn = zeile.getString("verliehen_an");
        artikel.rueckgabeDatum = (Long) zeile.getObject("rueckgabe_datum");
        artikel.zuletztGescannt = (Long) zeile.getObject("zuletzt_gescannt");
        artikel.scanWarnung = zeile.getString("scan_warnung");
        artikel.erstelltAm = zeile.getLong("erstellt_am");
        artikel.geaendertAm = zeile.getLong("geaendert_am");
        artikel.geloescht = zeile.getInt("geloescht") == 1;
        return artikel;
    };

    public List<Artikel> imTeam(String teamId) {
        return jdbc.query("SELECT * FROM artikel WHERE team_id = ? AND geloescht = 0"
                + " ORDER BY geaendert_am DESC", MAPPER, teamId);
    }

    /** Alles, was sich seit dem Zeitpunkt geaendert hat - inklusive Loeschungen. */
    public List<Artikel> geaendertSeit(String teamId, long seit) {
        return jdbc.query("SELECT * FROM artikel WHERE team_id = ? AND geaendert_am > ?"
                + " ORDER BY geaendert_am ASC", MAPPER, teamId, seit);
    }

    public Artikel nachId(String teamId, String id) {
        List<Artikel> treffer = jdbc.query("SELECT * FROM artikel WHERE team_id = ? AND id = ?",
                MAPPER, teamId, id);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    public Artikel nachKennung(String teamId, String kennung) {
        if (kennung == null || kennung.isEmpty()) {
            return null;
        }
        List<Artikel> treffer = jdbc.query("SELECT * FROM artikel"
                        + " WHERE team_id = ? AND rfid_uid = ? AND geloescht = 0 LIMIT 1",
                MAPPER, teamId, kennung);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    public Artikel anlegen(Artikel artikel) {
        artikel.id = artikel.id == null ? UUID.randomUUID().toString() : artikel.id;
        long jetzt = System.currentTimeMillis();
        artikel.erstelltAm = artikel.erstelltAm == 0 ? jetzt : artikel.erstelltAm;
        artikel.geaendertAm = jetzt;
        jdbc.update("INSERT INTO artikel (id, team_id, rfid_uid, name, beschreibung, kategorie,"
                        + " standort, lagerort, bild_url, status, verliehen_an, rueckgabe_datum,"
                        + " zuletzt_gescannt, scan_warnung, erstellt_am, geaendert_am, geloescht)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                artikel.id, artikel.teamId, leer(artikel.rfidUid), artikel.name,
                artikel.beschreibung, artikel.kategorie, artikel.standort, artikel.lagerort,
                artikel.bildUrl, artikel.status, artikel.verliehenAn, artikel.rueckgabeDatum,
                artikel.zuletztGescannt, artikel.scanWarnung, artikel.erstelltAm,
                artikel.geaendertAm);
        return artikel;
    }

    public Artikel aktualisieren(Artikel artikel) {
        artikel.geaendertAm = System.currentTimeMillis();
        jdbc.update("UPDATE artikel SET rfid_uid = ?, name = ?, beschreibung = ?, kategorie = ?,"
                        + " standort = ?, lagerort = ?, bild_url = ?, status = ?, verliehen_an = ?,"
                        + " rueckgabe_datum = ?, zuletzt_gescannt = ?, scan_warnung = ?,"
                        + " geaendert_am = ?, geloescht = ?"
                        + " WHERE team_id = ? AND id = ?",
                leer(artikel.rfidUid), artikel.name, artikel.beschreibung, artikel.kategorie,
                artikel.standort, artikel.lagerort, artikel.bildUrl, artikel.status,
                artikel.verliehenAn, artikel.rueckgabeDatum, artikel.zuletztGescannt,
                artikel.scanWarnung, artikel.geaendertAm, artikel.geloescht ? 1 : 0,
                artikel.teamId, artikel.id);
        return artikel;
    }

    /** Loeschen setzt nur ein Merkmal, damit andere Geraete es mitbekommen. */
    public void loeschen(String teamId, String id) {
        jdbc.update("UPDATE artikel SET geloescht = 1, rfid_uid = NULL, geaendert_am = ?"
                + " WHERE team_id = ? AND id = ?", System.currentTimeMillis(), teamId, id);
    }

    public int anzahl(String teamId) {
        Integer anzahl = jdbc.queryForObject(
                "SELECT COUNT(*) FROM artikel WHERE team_id = ? AND geloescht = 0",
                Integer.class, teamId);
        return anzahl == null ? 0 : anzahl;
    }

    private String leer(String wert) {
        return wert == null || wert.trim().isEmpty() ? null : wert.trim();
    }
}
