package de.tagstock.server.api;

import de.tagstock.server.daten.AnfrageDaten;
import de.tagstock.server.daten.ArtikelDaten;
import de.tagstock.server.daten.ProtokollDaten;
import de.tagstock.server.modell.Anfrage;
import de.tagstock.server.modell.Artikel;
import de.tagstock.server.modell.Benutzer;
import de.tagstock.server.modell.Protokoll;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Leih-Anfragen stellen und entscheiden. */
@RestController
@RequestMapping("/api/v1/teams/{teamId}/anfragen")
public class AnfrageController {

    public static class AnfrageEingabe {
        public String artikelId;
        public String nachricht;
        public Long datumVon;
        public Long datumBis;
    }

    public static class Entscheidung {
        public boolean genehmigt;
        public String antwort;
    }

    private final AnfrageDaten anfrageDaten;
    private final ArtikelDaten artikelDaten;
    private final ProtokollDaten protokollDaten;
    private final Zugriff zugriff;

    public AnfrageController(AnfrageDaten anfrageDaten, ArtikelDaten artikelDaten,
                             ProtokollDaten protokollDaten, Zugriff zugriff) {
        this.anfrageDaten = anfrageDaten;
        this.artikelDaten = artikelDaten;
        this.protokollDaten = protokollDaten;
        this.zugriff = zugriff;
    }

    @GetMapping
    public List<Anfrage> liste(HttpServletRequest anfrage, @PathVariable String teamId,
                               @RequestParam(required = false) String status) {
        zugriff.rolle(anfrage, teamId);
        return anfrageDaten.imTeam(teamId, status);
    }

    /** Jedes Mitglied darf anfragen - auch ohne Bearbeitungsrecht. */
    @PostMapping
    public Anfrage stellen(HttpServletRequest anfrage, @PathVariable String teamId,
                           @RequestBody AnfrageEingabe eingabe) {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        zugriff.rolle(anfrage, teamId);

        Artikel artikel = artikelDaten.nachId(teamId, eingabe.artikelId);
        if (artikel == null || artikel.geloescht) {
            throw ApiFehler.nichtGefunden("Artikel nicht gefunden");
        }

        Anfrage neu = new Anfrage();
        neu.teamId = teamId;
        neu.artikelId = artikel.id;
        neu.artikelName = artikel.name;
        neu.antragstellerId = benutzer.id;
        neu.antragstellerName = benutzer.name;
        neu.nachricht = eingabe.nachricht;
        neu.datumVon = eingabe.datumVon;
        neu.datumBis = eingabe.datumBis;
        return anfrageDaten.anlegen(neu);
    }

    /**
     * Entscheidet ueber eine Anfrage. Bei Zustimmung wird der Artikel direkt
     * auf verliehen gesetzt, damit der Bestand stimmt.
     */
    @PostMapping("/{anfrageId}/entscheiden")
    public Anfrage entscheiden(HttpServletRequest anfrage, @PathVariable String teamId,
                               @PathVariable String anfrageId,
                               @RequestBody Entscheidung entscheidung) {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        zugriff.rolleZumBearbeiten(anfrage, teamId);

        Anfrage vorhandene = anfrageDaten.nachId(teamId, anfrageId);
        if (vorhandene == null) {
            throw ApiFehler.nichtGefunden("Anfrage nicht gefunden");
        }
        if (!Anfrage.OFFEN.equals(vorhandene.status)) {
            throw ApiFehler.konflikt("Diese Anfrage wurde bereits entschieden");
        }

        String status = entscheidung.genehmigt ? Anfrage.GENEHMIGT : Anfrage.ABGELEHNT;
        anfrageDaten.entscheiden(teamId, anfrageId, status, benutzer.name, entscheidung.antwort);

        if (entscheidung.genehmigt) {
            Artikel artikel = artikelDaten.nachId(teamId, vorhandene.artikelId);
            if (artikel != null && !artikel.geloescht) {
                String vorher = artikel.status;
                artikel.status = "verliehen";
                artikel.verliehenAn = vorhandene.antragstellerName;
                artikel.rueckgabeDatum = vorhandene.datumBis;
                artikelDaten.aktualisieren(artikel);

                Protokoll eintrag = new Protokoll();
                eintrag.teamId = teamId;
                eintrag.artikelId = artikel.id;
                eintrag.artikelName = artikel.name;
                eintrag.aktion = "Status geändert";
                eintrag.alterWert = vorher;
                eintrag.neuerWert = "verliehen";
                eintrag.nutzer = benutzer.name;
                protokollDaten.anlegen(eintrag);
            }
        }
        return anfrageDaten.nachId(teamId, anfrageId);
    }
}
