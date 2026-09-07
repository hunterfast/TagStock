package de.tagstock.server.daten;

import de.tagstock.server.modell.Mitglied;
import de.tagstock.server.modell.Rollen;
import de.tagstock.server.modell.Team;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

/** Teams, Mitglieder und Einladungen. */
@Repository
public class TeamDaten {

    private static final String ZEICHEN = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom zufall = new SecureRandom();
    private final JdbcTemplate jdbc;

    public TeamDaten(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Team> TEAM_MAPPER = (zeile, nummer) -> {
        Team team = new Team();
        team.id = zeile.getString("id");
        team.name = zeile.getString("name");
        team.beschreibung = zeile.getString("beschreibung");
        team.erstellerId = zeile.getString("ersteller_id");
        team.erstelltAm = zeile.getLong("erstellt_am");
        return team;
    };

    public Team anlegen(String name, String beschreibung, String erstellerId) {
        Team team = new Team();
        team.id = UUID.randomUUID().toString();
        team.name = name;
        team.beschreibung = beschreibung;
        team.erstellerId = erstellerId;
        team.erstelltAm = System.currentTimeMillis();
        jdbc.update("INSERT INTO teams (id, name, beschreibung, ersteller_id, erstellt_am)"
                        + " VALUES (?, ?, ?, ?, ?)",
                team.id, team.name, team.beschreibung, team.erstellerId, team.erstelltAm);
        mitgliedSetzen(team.id, erstellerId, Rollen.ADMIN);
        team.rolle = Rollen.ADMIN;
        return team;
    }

    public Team nachId(String id) {
        List<Team> treffer = jdbc.query("SELECT * FROM teams WHERE id = ?", TEAM_MAPPER, id);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    /** Teams eines Kontos samt eigener Rolle. */
    public List<Team> fuerBenutzer(String benutzerId) {
        return jdbc.query("SELECT t.*, m.rolle AS eigene_rolle FROM teams t"
                        + " JOIN mitglieder m ON m.team_id = t.id"
                        + " WHERE m.benutzer_id = ? ORDER BY t.name COLLATE NOCASE",
                (zeile, nummer) -> {
                    Team team = TEAM_MAPPER.mapRow(zeile, nummer);
                    team.rolle = zeile.getString("eigene_rolle");
                    return team;
                }, benutzerId);
    }

    public void umbenennen(String teamId, String name, String beschreibung) {
        jdbc.update("UPDATE teams SET name = ?, beschreibung = ? WHERE id = ?",
                name, beschreibung, teamId);
    }

    public void loeschen(String teamId) {
        jdbc.update("DELETE FROM teams WHERE id = ?", teamId);
        jdbc.update("DELETE FROM mitglieder WHERE team_id = ?", teamId);
        jdbc.update("DELETE FROM artikel WHERE team_id = ?", teamId);
        jdbc.update("DELETE FROM kategorien WHERE team_id = ?", teamId);
        jdbc.update("DELETE FROM protokoll WHERE team_id = ?", teamId);
        jdbc.update("DELETE FROM anfragen WHERE team_id = ?", teamId);
        jdbc.update("DELETE FROM einladungen WHERE team_id = ?", teamId);
    }

    // -------------------------------------------------------------- Mitglieder

    public void mitgliedSetzen(String teamId, String benutzerId, String rolle) {
        jdbc.update("INSERT INTO mitglieder (team_id, benutzer_id, rolle, seit)"
                        + " VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT(team_id, benutzer_id) DO UPDATE SET rolle = excluded.rolle",
                teamId, benutzerId, rolle, System.currentTimeMillis());
    }

    public void mitgliedEntfernen(String teamId, String benutzerId) {
        jdbc.update("DELETE FROM mitglieder WHERE team_id = ? AND benutzer_id = ?",
                teamId, benutzerId);
    }

    /** Rolle im Team oder null, wenn das Konto nicht dazugehoert. */
    public String rolle(String teamId, String benutzerId) {
        List<String> treffer = jdbc.queryForList(
                "SELECT rolle FROM mitglieder WHERE team_id = ? AND benutzer_id = ?",
                String.class, teamId, benutzerId);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    public List<Mitglied> mitglieder(String teamId) {
        return jdbc.query("SELECT m.benutzer_id, m.rolle, m.seit, b.email, b.name"
                        + " FROM mitglieder m JOIN benutzer b ON b.id = m.benutzer_id"
                        + " WHERE m.team_id = ? ORDER BY b.name COLLATE NOCASE",
                (zeile, nummer) -> {
                    Mitglied mitglied = new Mitglied();
                    mitglied.benutzerId = zeile.getString("benutzer_id");
                    mitglied.rolle = zeile.getString("rolle");
                    mitglied.seit = zeile.getLong("seit");
                    mitglied.email = zeile.getString("email");
                    mitglied.name = zeile.getString("name");
                    return mitglied;
                }, teamId);
    }

    public int anzahlAdmins(String teamId) {
        Integer anzahl = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mitglieder WHERE team_id = ? AND rolle = ?",
                Integer.class, teamId, Rollen.ADMIN);
        return anzahl == null ? 0 : anzahl;
    }

    // ------------------------------------------------------------- Einladungen

    /** Erzeugt einen kurzen Code, mit dem jemand dem Team beitreten kann. */
    public String einladungAnlegen(String teamId, String rolle, long gueltigBis) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(ZEICHEN.charAt(zufall.nextInt(ZEICHEN.length())));
        }
        jdbc.update("INSERT INTO einladungen (code, team_id, rolle, erstellt_am, gueltig_bis)"
                        + " VALUES (?, ?, ?, ?, ?)",
                code.toString(), teamId, rolle, System.currentTimeMillis(), gueltigBis);
        return code.toString();
    }

    /** Loest eine Einladung ein und gibt das Team zurueck. */
    public Team einladungEinloesen(String code, String benutzerId, long jetzt) {
        List<String[]> treffer = jdbc.query(
                "SELECT team_id, rolle FROM einladungen WHERE code = ? AND gueltig_bis > ?",
                (zeile, nummer) -> new String[]{zeile.getString("team_id"), zeile.getString("rolle")},
                code, jetzt);
        if (treffer.isEmpty()) {
            return null;
        }
        String teamId = treffer.get(0)[0];
        String rolle = treffer.get(0)[1];
        mitgliedSetzen(teamId, benutzerId, rolle);
        Team team = nachId(teamId);
        if (team != null) {
            team.rolle = rolle;
        }
        return team;
    }
}
