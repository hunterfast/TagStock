package de.tagstock.server.api;

import de.tagstock.server.daten.TeamDaten;
import de.tagstock.server.modell.Team;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sicht der Betreuung auf den ganzen Server. Wer den Server betreut - das
 * erste angelegte Konto -, sieht hier jedes Lager, auch die, in denen er kein
 * Mitglied ist. Alles Weitere laeuft dann ueber die gewohnten Endpunkte.
 */
@RestController
@RequestMapping("/api/v1/verwaltung")
public class VerwaltungController {

    private final TeamDaten teamDaten;
    private final Zugriff zugriff;

    public VerwaltungController(TeamDaten teamDaten, Zugriff zugriff) {
        this.teamDaten = teamDaten;
        this.zugriff = zugriff;
    }

    @GetMapping("/teams")
    public List<Team> alleTeams(HttpServletRequest anfrage) {
        zugriff.verwalter(anfrage);
        return teamDaten.alle();
    }
}
