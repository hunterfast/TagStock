package de.tagstock.server.api;

import de.tagstock.server.daten.AnfrageDaten;
import de.tagstock.server.daten.ArtikelDaten;
import de.tagstock.server.daten.Bilderdienst;
import de.tagstock.server.daten.KategorieDaten;
import de.tagstock.server.daten.ProtokollDaten;
import de.tagstock.server.dienst.Packungen;
import de.tagstock.server.dienst.Zusammenfuehrung;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Abgleich mit der App. Die App holt sich alles, was sich seit ihrem letzten
 * Stand geaendert hat, und schickt ihre eigenen Aenderungen hoch. Haben zwei
 * Seiten denselben Artikel geaendert, werden die Aenderungen zusammengefuehrt
 * (siehe {@link Zusammenfuehrung}); Apps, die ihren Ausgangsstand noch nicht
 * mitschicken, bekommen wie frueher: der juengere Zeitstempel gewinnt.
 * Abgelehnte Faelle kommen mit dem Serverstand zurueck.
 */
@RestController
@RequestMapping("/api/v1/teams/{teamId}/sync")
public class SyncController {

    /** Was die App hochlaedt. */
    public static class Hochladen {
        public List<Hochgeladen> artikel = new ArrayList<>();
        public List<HochgeladenerEintrag> protokoll = new ArrayList<>();
    }

    /** Ein Artikel vom Geraet - samt dem Stand, auf dem es aufgesetzt hat. */
    public static class Hochgeladen extends Artikel {
        /** Leer bei Apps vor 2.3.0: Dann gewinnt die spaetere Fassung. */
        public Artikel basis;
    }

    /** Ein Protokolleintrag vom Geraet. */
    public static class HochgeladenerEintrag extends Protokoll {
        /**
         * Fuer Eintraege zu Artikeln, die erst mit diesem Abgleich auf den
         * Server kommen und darum noch keine Server-Kennung haben.
         */
        public Long lokaleArtikelId;
    }

    /** Rueckmeldung je hochgeladenem Artikel. */
    public static class Zuordnung {
        public Long lokaleId;
        public String serverId;
        public boolean angenommen;
        public String grund;
        /** Der Artikel ist hier schon geloescht - die App soll ihn auch loeschen. */
        public boolean geloescht;

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
        Map<Long, String> serverIds = new HashMap<>();
        for (Hochgeladen artikel : daten.artikel) {
            Zuordnung zuordnung = uebernehmen(teamId, artikel, benutzer);
            zuordnungen.add(zuordnung);
            if (artikel.lokaleId != null && zuordnung.serverId != null) {
                serverIds.put(artikel.lokaleId, zuordnung.serverId);
            }
        }
        for (HochgeladenerEintrag eintrag : daten.protokoll) {
            if ((eintrag.artikelId == null || eintrag.artikelId.isEmpty())
                    && eintrag.lokaleArtikelId != null) {
                eintrag.artikelId = serverIds.get(eintrag.lokaleArtikelId);
            }
            eintrag.teamId = teamId;
            if (eintrag.nutzer == null || eintrag.nutzer.isEmpty()) {
                eintrag.nutzer = benutzer.name;
            }
            protokollDaten.anlegen(eintrag);
        }

        Map<String, Object> ergebnis = abholen(anfrage, teamId, seit);
        ergebnis.put("artikel", mitAbgelehnten(teamId, ergebnis.get("artikel"), zuordnungen));
        ergebnis.put("zuordnungen", zuordnungen);
        return ergebnis;
    }

    /**
     * Abgelehntes kommt mit dem Serverstand zurueck - auch wenn der aelter ist
     * als der letzte Abgleich der App. Sonst behielte sie ihre abgelehnte
     * Fassung, und beide Seiten liefen auseinander.
     */
    @SuppressWarnings("unchecked")
    private List<Artikel> mitAbgelehnten(String teamId, Object geaendert,
                                         List<Zuordnung> zuordnungen) {
        List<Artikel> artikel = new ArrayList<>((List<Artikel>) geaendert);
        Set<String> dabei = new HashSet<>();
        for (Artikel eintrag : artikel) {
            dabei.add(eintrag.id);
        }
        for (Zuordnung zuordnung : zuordnungen) {
            if (zuordnung.angenommen || zuordnung.serverId == null
                    || !dabei.add(zuordnung.serverId)) {
                continue;
            }
            Artikel server = artikelDaten.nachId(teamId, zuordnung.serverId);
            if (server != null) {
                artikel.add(server);
            }
        }
        return artikel;
    }

    /** Einen hochgeladenen Artikel einsortieren. */
    private Zuordnung uebernehmen(String teamId, Hochgeladen artikel, Benutzer benutzer) {
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
        if (server.geloescht) {
            // Geloescht bleibt geloescht - eine spaete Aenderung von einem
            // Geraet, das es noch nicht wusste, holt ihn nicht zurueck.
            Zuordnung weg = new Zuordnung(artikel.lokaleId, server.id, false,
                    "Artikel wurde inzwischen gelöscht");
            weg.geloescht = true;
            return weg;
        }
        Artikel neu;
        if (artikel.basis != null) {
            // Was beide Seiten seit dem gemeinsamen Stand getan haben, bleibt.
            neu = Zusammenfuehrung.zusammenfuehren(artikel.basis, artikel, server);
        } else if (server.geaendertAm > artikel.geaendertAm) {
            // Auf dem Server ist eine juengere Fassung - die gewinnt.
            return new Zuordnung(artikel.lokaleId, server.id, false, "Server ist neuer");
        } else {
            neu = artikel;
        }
        Artikel belegt = artikelDaten.nachKennung(teamId, neu.rfidUid);
        if (belegt != null && !belegt.id.equals(server.id)) {
            return new Zuordnung(artikel.lokaleId, server.id, false,
                    "Kennung gehört bereits zu „" + belegt.name + "“");
        }

        neu.id = server.id;
        neu.teamId = teamId;
        neu.erstelltAm = server.erstelltAm;
        neu.geloescht = false;
        Packungen.geradeziehen(neu);
        if (server.bildUrl != null && (neu.bildUrl == null || neu.bildUrl.isEmpty())) {
            // Das Geraet hat das Bild entfernt - dann muss es auch hier weg.
            bilder.entfernen(teamId, server.id);
        }
        artikelDaten.aktualisieren(neu);
        return new Zuordnung(artikel.lokaleId, server.id, true, null);
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
