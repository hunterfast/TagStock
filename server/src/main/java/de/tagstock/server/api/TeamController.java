package de.tagstock.server.api;

import de.tagstock.server.daten.KategorieDaten;
import de.tagstock.server.daten.TeamDaten;
import de.tagstock.server.modell.Benutzer;
import de.tagstock.server.modell.Mitglied;
import de.tagstock.server.modell.Rollen;
import de.tagstock.server.modell.Team;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Teams anlegen, Mitglieder verwalten, per Code beitreten. */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    public static class TeamEingabe {
        public String name;
        public String beschreibung;
    }

    public static class BeitrittEingabe {
        public String code;
    }

    public static class RolleEingabe {
        public String rolle;
    }

    /** Gueltigkeit eines Einladungscodes: sieben Tage. */
    private static final long EINLADUNG_GUELTIG_MS = 7L * 24 * 60 * 60 * 1000;

    private final TeamDaten teamDaten;
    private final KategorieDaten kategorieDaten;
    private final Zugriff zugriff;

    public TeamController(TeamDaten teamDaten, KategorieDaten kategorieDaten, Zugriff zugriff) {
        this.teamDaten = teamDaten;
        this.kategorieDaten = kategorieDaten;
        this.zugriff = zugriff;
    }

    @GetMapping
    public List<Team> meine(HttpServletRequest anfrage) {
        return teamDaten.fuerBenutzer(zugriff.benutzer(anfrage).id);
    }

    @PostMapping
    public Team anlegen(HttpServletRequest anfrage, @RequestBody TeamEingabe eingabe) {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        if (eingabe.name == null || eingabe.name.trim().isEmpty()) {
            throw ApiFehler.ungueltig("Name fehlt");
        }
        Team team = teamDaten.anlegen(eingabe.name.trim(), eingabe.beschreibung, benutzer.id);
        kategorieDaten.standardAnlegen(team.id);
        return team;
    }

    @PutMapping("/{teamId}")
    public Team aendern(HttpServletRequest anfrage, @PathVariable String teamId,
                        @RequestBody TeamEingabe eingabe) {
        zugriff.rolleAlsAdmin(anfrage, teamId);
        if (eingabe.name == null || eingabe.name.trim().isEmpty()) {
            throw ApiFehler.ungueltig("Name fehlt");
        }
        teamDaten.umbenennen(teamId, eingabe.name.trim(), eingabe.beschreibung);
        Team team = teamDaten.nachId(teamId);
        team.rolle = Rollen.ADMIN;
        return team;
    }

    @DeleteMapping("/{teamId}")
    public Map<String, Object> loeschen(HttpServletRequest anfrage, @PathVariable String teamId) {
        zugriff.rolleAlsAdmin(anfrage, teamId);
        teamDaten.loeschen(teamId);
        return Map.of("geloescht", true);
    }

    @GetMapping("/{teamId}/mitglieder")
    public List<Mitglied> mitglieder(HttpServletRequest anfrage, @PathVariable String teamId) {
        zugriff.rolle(anfrage, teamId);
        return teamDaten.mitglieder(teamId);
    }

    /** Erzeugt einen Einladungscode; die Rolle gilt fuer alle, die ihn nutzen. */
    @PostMapping("/{teamId}/einladung")
    public Map<String, Object> einladung(HttpServletRequest anfrage, @PathVariable String teamId,
                                         @RequestBody(required = false) RolleEingabe eingabe) {
        zugriff.rolleAlsAdmin(anfrage, teamId);
        String rolle = eingabe == null || eingabe.rolle == null ? Rollen.MITGLIED : eingabe.rolle;
        if (!Rollen.gueltig(rolle)) {
            throw ApiFehler.ungueltig("Unbekannte Rolle");
        }
        long gueltigBis = System.currentTimeMillis() + EINLADUNG_GUELTIG_MS;
        String code = teamDaten.einladungAnlegen(teamId, rolle, gueltigBis);
        return Map.of("code", code, "rolle", rolle, "gueltigBis", gueltigBis);
    }

    @PostMapping("/beitreten")
    public Team beitreten(HttpServletRequest anfrage, @RequestBody BeitrittEingabe eingabe) {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        if (eingabe.code == null || eingabe.code.trim().isEmpty()) {
            throw ApiFehler.ungueltig("Code fehlt");
        }
        Team team = teamDaten.einladungEinloesen(eingabe.code.trim().toUpperCase(),
                benutzer.id, System.currentTimeMillis());
        if (team == null) {
            throw ApiFehler.nichtGefunden("Code ist ungültig oder abgelaufen");
        }
        return team;
    }

    @PutMapping("/{teamId}/mitglieder/{benutzerId}")
    public Map<String, Object> rolleSetzen(HttpServletRequest anfrage, @PathVariable String teamId,
                                           @PathVariable String benutzerId,
                                           @RequestBody RolleEingabe eingabe) {
        zugriff.rolleAlsAdmin(anfrage, teamId);
        if (!Rollen.gueltig(eingabe.rolle)) {
            throw ApiFehler.ungueltig("Unbekannte Rolle");
        }
        String bisher = teamDaten.rolle(teamId, benutzerId);
        if (bisher == null) {
            throw ApiFehler.nichtGefunden("Dieses Konto gehört nicht zum Team");
        }
        // Das letzte Adminkonto darf nicht herabgestuft werden.
        if (Rollen.istAdmin(bisher) && !Rollen.istAdmin(eingabe.rolle)
                && teamDaten.anzahlAdmins(teamId) <= 1) {
            throw ApiFehler.konflikt("Das Team braucht mindestens einen Admin");
        }
        teamDaten.mitgliedSetzen(teamId, benutzerId, eingabe.rolle);
        return Map.of("rolle", eingabe.rolle);
    }

    @DeleteMapping("/{teamId}/mitglieder/{benutzerId}")
    public Map<String, Object> entfernen(HttpServletRequest anfrage, @PathVariable String teamId,
                                         @PathVariable String benutzerId) {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        String eigene = zugriff.rolle(anfrage, teamId);
        boolean selbst = benutzer.id.equals(benutzerId);
        if (!selbst && !Rollen.istAdmin(eigene)) {
            throw ApiFehler.verboten("Nur Admins dürfen Mitglieder entfernen");
        }
        String rolle = teamDaten.rolle(teamId, benutzerId);
        if (rolle == null) {
            throw ApiFehler.nichtGefunden("Dieses Konto gehört nicht zum Team");
        }
        if (Rollen.istAdmin(rolle) && teamDaten.anzahlAdmins(teamId) <= 1) {
            throw ApiFehler.konflikt("Das Team braucht mindestens einen Admin");
        }
        teamDaten.mitgliedEntfernen(teamId, benutzerId);
        return Map.of("entfernt", true);
    }
}
