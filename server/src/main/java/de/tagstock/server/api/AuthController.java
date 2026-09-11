package de.tagstock.server.api;

import de.tagstock.server.daten.BenutzerDaten;
import de.tagstock.server.daten.TeamDaten;
import de.tagstock.server.modell.Benutzer;
import de.tagstock.server.modell.Team;
import de.tagstock.server.sicherheit.Passwoerter;
import de.tagstock.server.sicherheit.Tokens;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Registrierung, Anmeldung und Auskunft ueber das eigene Konto. */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    /** Eingabe fuer Registrierung und Anmeldung. */
    public static class Zugangsdaten {
        public String email;
        public String name;
        public String passwort;
        public String code;
    }

    private final BenutzerDaten benutzerDaten;
    private final TeamDaten teamDaten;
    private final Passwoerter passwoerter;
    private final Tokens tokens;

    @Value("${tagstock.token-gueltigkeit-tage:180}")
    private int gueltigkeitTage;

    @Value("${tagstock.registrierungs-code:}")
    private String registrierungsCode;

    public AuthController(BenutzerDaten benutzerDaten, TeamDaten teamDaten,
                          Passwoerter passwoerter, Tokens tokens) {
        this.benutzerDaten = benutzerDaten;
        this.teamDaten = teamDaten;
        this.passwoerter = passwoerter;
        this.tokens = tokens;
    }

    @PostMapping("/auth/registrieren")
    public Map<String, Object> registrieren(@RequestBody Zugangsdaten daten) {
        String email = pflicht(daten.email, "E-Mail").toLowerCase();
        String passwort = pflicht(daten.passwort, "Passwort");
        if (passwort.length() < 8) {
            throw ApiFehler.ungueltig("Das Passwort braucht mindestens 8 Zeichen");
        }
        // Der erste Zugang darf immer angelegt werden; danach greift der Code,
        // sofern einer gesetzt ist.
        boolean ersterZugang = benutzerDaten.anzahl() == 0;
        if (!ersterZugang && registrierungsCode != null && !registrierungsCode.isEmpty()
                && !registrierungsCode.equals(daten.code)) {
            throw ApiFehler.verboten("Registrierung nur mit gültigem Code");
        }
        if (benutzerDaten.nachEmail(email) != null) {
            throw ApiFehler.konflikt("Diese E-Mail ist bereits vergeben");
        }
        String name = daten.name == null || daten.name.trim().isEmpty()
                ? email : daten.name.trim();
        // Das erste Konto betreut den Server und sieht damit jedes Lager.
        Benutzer benutzer = benutzerDaten.anlegen(email, name, passwoerter.hashen(passwort),
                ersterZugang);
        return antwort(benutzer);
    }

    @PostMapping("/auth/anmelden")
    public Map<String, Object> anmelden(@RequestBody Zugangsdaten daten) {
        String email = pflicht(daten.email, "E-Mail").toLowerCase();
        Benutzer benutzer = benutzerDaten.nachEmail(email);
        if (benutzer == null || !passwoerter.passt(daten.passwort, benutzer.passwort)) {
            throw new ApiFehler(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "E-Mail oder Passwort stimmt nicht");
        }
        return antwort(benutzer);
    }

    @PostMapping("/auth/abmelden")
    public Map<String, Object> abmelden(HttpServletRequest anfrage) {
        String kopf = anfrage.getHeader("Authorization");
        if (kopf != null && kopf.startsWith("Bearer ")) {
            benutzerDaten.sitzungBeenden(tokens.hash(kopf.substring(7).trim()));
        }
        return Map.of("abgemeldet", true);
    }

    @GetMapping("/ich")
    public Map<String, Object> ich(HttpServletRequest anfrage) {
        Benutzer benutzer = angemeldeter(anfrage);
        List<Team> teams = teamDaten.fuerBenutzer(benutzer.id);
        Map<String, Object> ergebnis = new HashMap<>();
        ergebnis.put("benutzer", benutzer);
        ergebnis.put("teams", teams);
        return ergebnis;
    }

    @PostMapping("/ich/name")
    public Map<String, Object> namenAendern(HttpServletRequest anfrage,
                                            @RequestBody Zugangsdaten daten) {
        Benutzer benutzer = angemeldeter(anfrage);
        String name = pflicht(daten.name, "Name");
        benutzerDaten.namenAendern(benutzer.id, name);
        benutzer.name = name;
        return Map.of("benutzer", benutzer);
    }

    private Benutzer angemeldeter(HttpServletRequest anfrage) {
        Benutzer benutzer = de.tagstock.server.sicherheit.Angemeldet.aus(anfrage);
        if (benutzer == null) {
            throw ApiFehler.anmeldungNoetig();
        }
        return benutzer;
    }

    /** Legt eine Sitzung an und gibt Token samt Konto zurueck. */
    private Map<String, Object> antwort(Benutzer benutzer) {
        String token = tokens.neu();
        long gueltigBis = System.currentTimeMillis()
                + (long) gueltigkeitTage * 24 * 60 * 60 * 1000;
        benutzerDaten.abgelaufeneAufraeumen(System.currentTimeMillis());
        benutzerDaten.sitzungAnlegen(tokens.hash(token), benutzer.id, gueltigBis);

        Map<String, Object> ergebnis = new HashMap<>();
        ergebnis.put("token", token);
        ergebnis.put("gueltigBis", gueltigBis);
        ergebnis.put("benutzer", benutzer);
        ergebnis.put("teams", teamDaten.fuerBenutzer(benutzer.id));
        return ergebnis;
    }

    private String pflicht(String wert, String feld) {
        if (wert == null || wert.trim().isEmpty()) {
            throw ApiFehler.ungueltig(feld + " fehlt");
        }
        return wert.trim();
    }
}
