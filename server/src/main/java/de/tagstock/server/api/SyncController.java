package de.tagstock.server.api;

import de.tagstock.server.daten.AnfrageDaten;
import de.tagstock.server.daten.ArtikelDaten;
import de.tagstock.server.daten.Bilderdienst;
import de.tagstock.server.daten.KategorieDaten;
import de.tagstock.server.daten.ProtokollDaten;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abgleich mit der App. Die App holt sich alles, was sich seit ihrem letzten
 * Stand geaendert hat, und schickt ihre eigenen Aenderungen hoch. Bei
 * Doppelaenderungen gewinnt der juengere Zeitstempel; abgelehnte Faelle kommen
 * mit dem Serverstand zurueck.
 */
@RestController
@RequestMapping("/api/v1/teams/{teamId}/sync")
public class SyncController {

    /** Was die App hochlaedt. */
    public static class Hochladen {
        public List<Artikel> artikel = new ArrayList<>();
        public List<Protokoll> protokoll = new ArrayList<>();
    }

    /** Rueckmeldung je hochgeladenem Artikel. */
    public static class Zuordnung {
        public Long lokaleId;
        public String serverId;
        public boolean angenommen;
        public String grund;

        Zuordnung(Long lokaleId, String serverId, boolean angenommen, String grund) {
            this.lokaleId = lokaleId;
            this.serverId = serverId;
            this.angenommen = angenommen;
            this.grund = grund;
        }
    }

    private final ArtikelDaten artikelDaten;
    private final KategorieDaten kategorieDaten;
    private final ProtokollDaten protokollDaten;
    private final AnfrageDaten anfrageDaten;
    private final Bilderdienst bilder;
    private final Zugriff zugriff;

    public SyncController(ArtikelDaten artikelDaten, KategorieDaten kategorieDaten,
                          ProtokollDaten protokollDaten, AnfrageDaten anfrageDaten,
                          Bilderdienst bilder, Zugriff zugriff) {
        this.artikelDaten = artikelDaten;
        this.kategorieDaten = kategorieDaten;
        this.protokollDaten = protokollDaten;
        this.anfrageDaten = anfrageDaten;
        this.bilder = bilder;
        this.zugriff = zugriff;
    }

    /** Alles, was sich seit dem angegebenen Zeitpunkt geaendert hat. */
    @GetMapping
    public Map<String, Object> abholen(HttpServletRequest anfrage, @PathVariable String teamId,
                                       @RequestParam(defaultValue = "0") long seit) {
        zugriff.rolle(anfrage, teamId);
        long stand = System.currentTimeMillis();

        Map<String, Object> ergebnis = new HashMap<>();
        ergebnis.put("stand", stand);
        ergebnis.put("artikel", artikelDaten.geaendertSeit(teamId, seit));
        ergebnis.put("kategorien", kategorieDaten.geaendertSeit(teamId, seit));
        ergebnis.put("protokoll", protokollDaten.seit(teamId, seit));
        ergebnis.put("anfragen", anfrageDaten.geaendertSeit(teamId, seit));
        return ergebnis;
    }

    @PostMapping
    public Map<String, Object> hochladen(HttpServletRequest anfrage, @PathVariable String teamId,
                                         @RequestParam(defaultValue = "0") long seit,
                                         @RequestBody Hochladen daten) {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        zugriff.rolleZumBearbeiten(anfrage, teamId);

        List<Zuordnung> zuordnungen = new ArrayList<>();
        for (Artikel artikel : daten.artikel) {
            zuordnungen.add(uebernehmen(teamId, artikel, benutzer));
        }
        for (Protokoll eintrag : daten.protokoll) {
            eintrag.teamId = teamId;
            if (eintrag.nutzer == null || eintrag.nutzer.isEmpty()) {
                eintrag.nutzer = benutzer.name;
            }
            protokollDaten.anlegen(eintrag);
        }

        Map<String, Object> ergebnis = abholen(anfrage, teamId, seit);
        ergebnis.put("zuordnungen", zuordnungen);
        return ergebnis;
    }

    /** Einen hochgeladenen Artikel einsortieren. */
    private Zuordnung uebernehmen(String teamId, Artikel artikel, Benutzer benutzer) {
        if (artikel.name == null || artikel.name.trim().isEmpty()) {
            return new Zuordnung(artikel.lokaleId, null, false, "Bezeichnung fehlt");
        }
        artikel.teamId = teamId;

        if (artikel.id == null || artikel.id.isEmpty()) {
            Artikel belegt = artikelDaten.nachKennung(teamId, artikel.rfidUid);
            if (belegt != null) {
                // Die Kennung gibt es schon: die App uebernimmt den Serverartikel.
                return new Zuordnung(artikel.lokaleId, belegt.id, false,
                        "Kennung gehört bereits zu „" + belegt.name + "“");
            }
            Artikel angelegt = artikelDaten.anlegen(artikel);
            protokollDaten.anlegen(eintrag(teamId, angelegt, "Artikel erstellt", null,
                    angelegt.name, benutzer.name));
            return new Zuordnung(artikel.lokaleId, angelegt.id, true, null);
        }

        Artikel server = artikelDaten.nachId(teamId, artikel.id);
        if (server == null) {
            return new Zuordnung(artikel.lokaleId, artikel.id, false, "Artikel gibt es nicht mehr");
        }
        if (server.geaendertAm > artikel.geaendertAm) {
            // Auf dem Server ist eine juengere Fassung - die gewinnt.
            return new Zuordnung(artikel.lokaleId, server.id, false, "Server ist neuer");
        }
        Artikel belegt = artikelDaten.nachKennung(teamId, artikel.rfidUid);
        if (belegt != null && !belegt.id.equals(artikel.id)) {
            return new Zuordnung(artikel.lokaleId, server.id, false,
                    "Kennung gehört bereits zu „" + belegt.name + "“");
        }

        artikel.erstelltAm = server.erstelltAm;
        if (server.bildUrl != null && (artikel.bildUrl == null || artikel.bildUrl.isEmpty())) {
            // Das Geraet hat das Bild entfernt - dann muss es auch hier weg.
            bilder.entfernen(teamId, artikel.id);
        }
        artikelDaten.aktualisieren(artikel);
        return new Zuordnung(artikel.lokaleId, artikel.id, true, null);
    }

    private Protokoll eintrag(String teamId, Artikel artikel, String aktion, String alt,
                              String neu, String nutzer) {
        Protokoll eintrag = new Protokoll();
        eintrag.teamId = teamId;
        eintrag.artikelId = artikel.id;
        eintrag.artikelName = artikel.name;
        eintrag.aktion = aktion;
        eintrag.alterWert = alt;
        eintrag.neuerWert = neu;
        eintrag.nutzer = nutzer;
        return eintrag;
    }
}
