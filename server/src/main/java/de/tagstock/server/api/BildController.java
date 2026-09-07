package de.tagstock.server.api;

import de.tagstock.server.daten.ArtikelDaten;
import de.tagstock.server.daten.Bilderdienst;
import de.tagstock.server.daten.Bildspeicher;
import de.tagstock.server.modell.Artikel;
import de.tagstock.server.modell.Benutzer;
import de.tagstock.server.modell.Bild;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Artikelbilder liegen auf dem Server, nicht auf dem Geraet. Die App schickt
 * die Aufnahme als Koerper der Anfrage hoch und bekommt eine Adresse zurueck,
 * unter der jedes Mitglied des Teams sie abholen kann.
 */
@RestController
@RequestMapping("/api/v1/teams/{teamId}")
public class BildController {

    private final ArtikelDaten artikelDaten;
    private final Bilderdienst bilder;
    private final Zugriff zugriff;

    public BildController(ArtikelDaten artikelDaten, Bilderdienst bilder, Zugriff zugriff) {
        this.artikelDaten = artikelDaten;
        this.bilder = bilder;
        this.zugriff = zugriff;
    }

    /** Bild eines Artikels ablegen. Ein vorheriges Bild wird dabei ersetzt. */
    @PostMapping("/artikel/{artikelId}/bild")
    public Map<String, Object> hochladen(HttpServletRequest anfrage, @PathVariable String teamId,
                                         @PathVariable String artikelId) throws IOException {
        Benutzer benutzer = zugriff.benutzer(anfrage);
        zugriff.rolleZumBearbeiten(anfrage, teamId);

        Artikel artikel = artikelDaten.nachId(teamId, artikelId);
        if (artikel == null || artikel.geloescht) {
            throw ApiFehler.nichtGefunden("Artikel nicht gefunden");
        }

        Bildspeicher speicher = bilder.speicher();
        byte[] inhalt = speicher.einlesen(anfrage.getInputStream());
        if (inhalt == null) {
            throw new ApiFehler(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Bild ist größer als " + speicher.maxMb() + " MB");
        }
        String typ = Bildspeicher.typAusInhalt(inhalt);
        if (typ == null) {
            throw ApiFehler.ungueltig("Nur JPEG, PNG oder WebP");
        }

        Bild bild = bilder.ablegen(teamId, artikelId, benutzer.id, typ, inhalt);
        artikel.bildUrl = Bilderdienst.adresse(teamId, bild.id);
        artikelDaten.aktualisieren(artikel);

        Map<String, Object> antwort = new HashMap<>();
        antwort.put("id", bild.id);
        antwort.put("bildUrl", artikel.bildUrl);
        antwort.put("typ", typ);
        antwort.put("groesse", bild.groesse);
        antwort.put("geaendertAm", artikel.geaendertAm);
        return antwort;
    }

    /** Bild ausliefern. Der Inhalt aendert sich nie, deshalb darf er lange liegen bleiben. */
    @GetMapping("/bilder/{bildId}")
    public ResponseEntity<byte[]> holen(HttpServletRequest anfrage, @PathVariable String teamId,
                                        @PathVariable String bildId) throws IOException {
        zugriff.rolle(anfrage, teamId);
        Bild bild = bilder.nachId(teamId, bildId);
        if (bild == null) {
            throw ApiFehler.nichtGefunden("Bild nicht gefunden");
        }
        byte[] inhalt = bilder.inhalt(bild);
        if (inhalt == null) {
            throw ApiFehler.nichtGefunden("Bilddatei fehlt");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bild.typ))
                .eTag("\"" + bild.id + "\"")
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate())
                .body(inhalt);
    }

    @DeleteMapping("/artikel/{artikelId}/bild")
    public Map<String, Object> loeschen(HttpServletRequest anfrage, @PathVariable String teamId,
                                        @PathVariable String artikelId) {
        zugriff.rolleZumBearbeiten(anfrage, teamId);
        Artikel artikel = artikelDaten.nachId(teamId, artikelId);
        if (artikel == null) {
            throw ApiFehler.nichtGefunden("Artikel nicht gefunden");
        }
        bilder.entfernen(teamId, artikelId);
        artikel.bildUrl = null;
        artikelDaten.aktualisieren(artikel);
        return Map.of("geloescht", true);
    }
}
