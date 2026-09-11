package de.tagstock.server.daten;

import de.tagstock.server.modell.Benutzer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Konten und Sitzungen. */
@Repository
public class BenutzerDaten {

    private final JdbcTemplate jdbc;

    public BenutzerDaten(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Benutzer> MAPPER = (zeile, nummer) -> {
        Benutzer benutzer = new Benutzer();
        benutzer.id = zeile.getString("id");
        benutzer.email = zeile.getString("email");
        benutzer.name = zeile.getString("name");
        benutzer.passwort = zeile.getString("passwort");
        benutzer.verwalter = zeile.getInt("verwalter") == 1;
        benutzer.erstelltAm = zeile.getLong("erstellt_am");
        return benutzer;
    };

    public Benutzer anlegen(String email, String name, String passwortHash, boolean verwalter) {
        Benutzer benutzer = new Benutzer(UUID.randomUUID().toString(), email, name,
                System.currentTimeMillis());
        benutzer.passwort = passwortHash;
        benutzer.verwalter = verwalter;
        jdbc.update("INSERT INTO benutzer (id, email, name, passwort, verwalter, erstellt_am)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                benutzer.id, benutzer.email, benutzer.name, passwortHash,
                verwalter ? 1 : 0, benutzer.erstelltAm);
        return benutzer;
    }

    public Benutzer nachEmail(String email) {
        List<Benutzer> treffer = jdbc.query(
                "SELECT * FROM benutzer WHERE email = ? COLLATE NOCASE", MAPPER, email);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    public Benutzer nachId(String id) {
        List<Benutzer> treffer = jdbc.query("SELECT * FROM benutzer WHERE id = ?", MAPPER, id);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    public int anzahl() {
        Integer anzahl = jdbc.queryForObject("SELECT COUNT(*) FROM benutzer", Integer.class);
        return anzahl == null ? 0 : anzahl;
    }

    public void namenAendern(String benutzerId, String name) {
        jdbc.update("UPDATE benutzer SET name = ? WHERE id = ?", name, benutzerId);
    }

    // --------------------------------------------------------------- Sitzungen

    public void sitzungAnlegen(String tokenHash, String benutzerId, long gueltigBis) {
        jdbc.update("INSERT INTO sitzungen (token_hash, benutzer_id, erstellt_am, gueltig_bis)"
                        + " VALUES (?, ?, ?, ?)",
                tokenHash, benutzerId, System.currentTimeMillis(), gueltigBis);
    }

    public Benutzer zuToken(String tokenHash, long jetzt) {
        List<Benutzer> treffer = jdbc.query(
                "SELECT b.* FROM benutzer b JOIN sitzungen s ON s.benutzer_id = b.id"
                        + " WHERE s.token_hash = ? AND s.gueltig_bis > ?",
                MAPPER, tokenHash, jetzt);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    public void sitzungBeenden(String tokenHash) {
        jdbc.update("DELETE FROM sitzungen WHERE token_hash = ?", tokenHash);
    }

    public void abgelaufeneAufraeumen(long jetzt) {
        jdbc.update("DELETE FROM sitzungen WHERE gueltig_bis <= ?", jetzt);
    }
}
