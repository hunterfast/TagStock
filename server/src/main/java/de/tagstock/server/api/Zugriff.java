package de.tagstock.server.api;

import de.tagstock.server.daten.TeamDaten;
import de.tagstock.server.modell.Benutzer;
import de.tagstock.server.modell.Rollen;
import de.tagstock.server.sicherheit.Angemeldet;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

/** Prueft Anmeldung und Rolle - jede geschuetzte Anfrage laeuft hier durch. */
@Component
public class Zugriff {

    private final TeamDaten teamDaten;

    public Zugriff(TeamDaten teamDaten) {
        this.teamDaten = teamDaten;
    }

    public Benutzer benutzer(HttpServletRequest anfrage) {
        Benutzer benutzer = Angemeldet.aus(anfrage);
        if (benutzer == null) {
            throw ApiFehler.anmeldungNoetig();
        }
        return benutzer;
    }

    /** Rolle im Team; wirft, wenn das Konto nicht dazugehoert. */
    public String rolle(HttpServletRequest anfrage, String teamId) {
        Benutzer benutzer = benutzer(anfrage);
        String rolle = teamDaten.rolle(teamId, benutzer.id);
        if (rolle == null) {
            throw ApiFehler.verboten("Kein Zugriff auf dieses Team");
        }
        return rolle;
    }

    public String rolleZumBearbeiten(HttpServletRequest anfrage, String teamId) {
        String rolle = rolle(anfrage, teamId);
        if (!Rollen.darfBearbeiten(rolle)) {
            throw ApiFehler.verboten("Nur Lageristen und Admins duerfen den Bestand aendern");
        }
        return rolle;
    }

    public String rolleAlsAdmin(HttpServletRequest anfrage, String teamId) {
        String rolle = rolle(anfrage, teamId);
        if (!Rollen.istAdmin(rolle)) {
            throw ApiFehler.verboten("Nur Admins duerfen das");
        }
        return rolle;
    }
}
