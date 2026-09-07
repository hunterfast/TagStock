package de.tagstock.server.daten;

import de.tagstock.server.modell.Bild;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Bilddatei und Eintrag gehoeren zusammen - hier laufen beide Seiten zusammen. */
@Component
public class Bilderdienst {

    private final BildDaten daten;
    private final Bildspeicher speicher;

    public Bilderdienst(BildDaten daten, Bildspeicher speicher) {
        this.daten = daten;
        this.speicher = speicher;
    }

    public Bildspeicher speicher() {
        return speicher;
    }

    public Bild nachId(String teamId, String bildId) {
        return daten.nachId(teamId, bildId);
    }

    public byte[] inhalt(Bild bild) throws IOException {
        return speicher.lesen(bild.datei);
    }

    /** Legt ein Bild ab und raeumt die vorherigen Bilder des Artikels weg. */
    public Bild ablegen(String teamId, String artikelId, String benutzerId, String typ,
                        byte[] inhalt) {
        List<Bild> vorherige = daten.zuArtikel(teamId, artikelId);

        Bild bild = new Bild();
        bild.id = UUID.randomUUID().toString();
        bild.teamId = teamId;
        bild.artikelId = artikelId;
        bild.typ = typ;
        bild.groesse = inhalt.length;
        bild.hochgeladenVon = benutzerId;
        bild.datei = speicher.schreiben(teamId, bild.id, typ, inhalt);
        daten.anlegen(bild);

        // Erst wegraeumen, wenn das neue Bild sicher liegt.
        for (Bild alt : vorherige) {
            speicher.loeschen(alt.datei);
            daten.loeschen(teamId, alt.id);
        }
        return bild;
    }

    public void entfernen(String teamId, String artikelId) {
        for (Bild bild : daten.zuArtikel(teamId, artikelId)) {
            speicher.loeschen(bild.datei);
            daten.loeschen(teamId, bild.id);
        }
    }

    /** Adresse, unter der die App das Bild abholt. */
    public static String adresse(String teamId, String bildId) {
        return "/api/v1/teams/" + teamId + "/bilder/" + bildId;
    }
}
