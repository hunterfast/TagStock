package de.tagstock.server.api;

import de.tagstock.server.daten.ArtikelDaten;
import de.tagstock.server.daten.Bilderdienst;
import de.tagstock.server.daten.KategorieDaten;
import de.tagstock.server.daten.ProtokollDaten;
import de.tagstock.server.modell.Artikel;
import de.tagstock.server.modell.Benutzer;
import de.tagstock.server.modell.Kategorie;
import de.tagstock.server.modell.Protokoll;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Bestand eines Teams: lesen, anlegen, aendern, loeschen. */
@RestController
@RequestMapping("/api/v1/teams/{teamId}")
public class ArtikelController {

    private final ArtikelDaten artikelDaten;
    private final KategorieDaten kategorieDaten;
    private final ProtokollDaten protokollDaten;
    private final Bilderdienst bilder;
    private final Zugriff zugriff;

    public ArtikelController(ArtikelDaten artikelDaten, KategorieDaten kategorieDaten,
                             ProtokollDaten protokollDaten, Bilderdienst bilder,
                             Zugriff zugriff) {
        this.artikelDaten = artikelDaten;
        this.kategorieDaten = kategorieDaten;
        this.protokollDaten = protokollDaten;
        this.bilder = bilder;
        this.zugriff = zugriff;
    }

    @GetMapping("/artikel")
    public List<Artikel> liste(HttpServletRequest anfrage, @PathVariable String teamId) {
        zugriff.rolle(anfrage, teamId);
        return artikelDaten.imTeam(teamId);
    }

    @GetMapping("/artikel/suche")
    public Artikel nachKennung(HttpServletRequest anfrage, @PathVariable String teamId,
                               @RequestParam String kennung) {
        zugriff.rolle(anfrage, teamId);
        Artikel artikel = artikelDaten.nachKennung(teamId, kennung);
        if (artikel == null) {
            throw ApiFehler.nichtGefunden("Kein Artikel mit dieser Kennung");
        }
        return artikel;
    }

    @PostMapping("/artikel")
    public Artikel anlegen(HttpServletRequest anfrage, @PathVariable String teamId,
                           @RequestBody Artikel eingabe) {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        zugriff.rolleZumBearbeiten(anfrage, teamId);
        pruefeName(eingabe);
        pruefeKennung(teamId, eingabe.rfidUid, null);

        eingabe.teamId = teamId;
        eingabe.id = null;
        eingabe.geloescht = false;
        Artikel angelegt = artikelDaten.anlegen(eingabe);
        protokoll(teamId, angelegt, "Artikel erstellt", null, angelegt.name, benutzer.name);
        return angelegt;
    }

    @PutMapping("/artikel/{artikelId}")
    public Artikel aendern(HttpServletRequest anfrage, @PathVariable String teamId,
                           @PathVariable String artikelId, @RequestBody Artikel eingabe) {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        zugriff.rolleZumBearbeiten(anfrage, teamId);
        Artikel vorher = artikelDaten.nachId(teamId, artikelId);
        if (vorher == null || vorher.geloescht) {
            throw ApiFehler.nichtGefunden("Artikel nicht gefunden");
        }
        pruefeName(eingabe);
        pruefeKennung(teamId, eingabe.rfidUid, artikelId);

        eingabe.id = artikelId;
        eingabe.teamId = teamId;
        eingabe.erstelltAm = vorher.erstelltAm;
        eingabe.geloescht = false;
        Artikel neu = artikelDaten.aktualisieren(eingabe);
        unterschiedeProtokollieren(teamId, vorher, neu, benutzer.name);
        return neu;
    }

    @DeleteMapping("/artikel/{artikelId}")
    public Map<String, Object> loeschen(HttpServletRequest anfrage, @PathVariable String teamId,
                                        @PathVariable String artikelId) {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        zugriff.rolleZumBearbeiten(anfrage, teamId);
        Artikel vorher = artikelDaten.nachId(teamId, artikelId);
        if (vorher == null) {
            throw ApiFehler.nichtGefunden("Artikel nicht gefunden");
        }
        bilder.entfernen(teamId, artikelId);
        artikelDaten.loeschen(teamId, artikelId);
        protokoll(teamId, vorher, "Artikel gelöscht", vorher.name, null, benutzer.name);
        return Map.of("geloescht", true);
    }

    @GetMapping("/artikel/{artikelId}/protokoll")
    public List<Protokoll> protokoll(HttpServletRequest anfrage, @PathVariable String teamId,
                                     @PathVariable String artikelId) {
        zugriff.rolle(anfrage, teamId);
        return protokollDaten.zuArtikel(teamId, artikelId);
    }

    // ------------------------------------------------------------- Kategorien

    @GetMapping("/kategorien")
    public List<Kategorie> kategorien(HttpServletRequest anfrage, @PathVariable String teamId) {
        zugriff.rolle(anfrage, teamId);
        return kategorieDaten.imTeam(teamId);
    }

    @PostMapping("/kategorien")
    public Kategorie kategorieAnlegen(HttpServletRequest anfrage, @PathVariable String teamId,
                                      @RequestBody Kategorie eingabe) {
        zugriff.rolleZumBearbeiten(anfrage, teamId);
        if (eingabe.name == null || eingabe.name.trim().isEmpty()) {
            throw ApiFehler.ungueltig("Name fehlt");
        }
        return kategorieDaten.anlegen(teamId, eingabe.name.trim(), eingabe.reihenfolge);
    }

    @PutMapping("/kategorien/{kategorieId}")
    public Kategorie kategorieAendern(HttpServletRequest anfrage, @PathVariable String teamId,
                                      @PathVariable String kategorieId,
                                      @RequestBody Kategorie eingabe) {
        zugriff.rolleZumBearbeiten(anfrage, teamId);
        eingabe.id = kategorieId;
        eingabe.teamId = teamId;
        kategorieDaten.aktualisieren(eingabe);
        return eingabe;
    }

    @DeleteMapping("/kategorien/{kategorieId}")
    public Map<String, Object> kategorieLoeschen(HttpServletRequest anfrage,
                                                 @PathVariable String teamId,
                                                 @PathVariable String kategorieId) {
        zugriff.rolleZumBearbeiten(anfrage, teamId);
        kategorieDaten.loeschen(teamId, kategorieId);
        return Map.of("geloescht", true);
    }

    // ----------------------------------------------------------------- Hilfen

    private void pruefeName(Artikel artikel) {
        if (artikel.name == null || artikel.name.trim().isEmpty()) {
            throw ApiFehler.ungueltig("Bezeichnung fehlt");
        }
        artikel.name = artikel.name.trim();
    }

    /** Eine Kennung darf im Team nur einem Artikel gehoeren. */
    private void pruefeKennung(String teamId, String kennung, String eigeneId) {
        if (kennung == null || kennung.trim().isEmpty()) {
            return;
        }
        Artikel belegt = artikelDaten.nachKennung(teamId, kennung.trim());
        if (belegt != null && !belegt.id.equals(eigeneId)) {
            throw ApiFehler.konflikt("Kennung gehört bereits zu „" + belegt.name + "“");
        }
    }

    private void unterschiedeProtokollieren(String teamId, Artikel vorher, Artikel neu,
                                            String nutzer) {
        if (!gleich(vorher.name, neu.name)) {
            protokoll(teamId, neu, "Name geändert", vorher.name, neu.name, nutzer);
        }
        if (!gleich(vorher.status, neu.status)) {
            protokoll(teamId, neu, "Status geändert", vorher.status, neu.status, nutzer);
        }
        if (!gleich(vorher.standort, neu.standort)) {
            protokoll(teamId, neu, "Standort geändert", vorher.standort, neu.standort, nutzer);
        }
        if (!gleich(vorher.lagerort, neu.lagerort)) {
            protokoll(teamId, neu, "Lagerort geändert", vorher.lagerort, neu.lagerort, nutzer);
        }
    }

    private void protokoll(String teamId, Artikel artikel, String aktion, String alt, String neu,
                           String nutzer) {
        Protokoll eintrag = new Protokoll();
        eintrag.teamId = teamId;
        eintrag.artikelId = artikel.id;
        eintrag.artikelName = artikel.name;
        eintrag.aktion = aktion;
        eintrag.alterWert = alt;
        eintrag.neuerWert = neu;
        eintrag.nutzer = nutzer;
        protokollDaten.anlegen(eintrag);
    }

    private boolean gleich(String a, String b) {
        String linke = a == null ? "" : a.trim();
        String rechte = b == null ? "" : b.trim();
        return linke.equals(rechte);
    }
}
