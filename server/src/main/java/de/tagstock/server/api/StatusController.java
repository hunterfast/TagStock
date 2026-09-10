package de.tagstock.server.api;

import de.tagstock.server.dienst.Produktsuche;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Auskunft ohne Anmeldung: laeuft der Server, und passt die Version? */
@RestController
@RequestMapping("/api/v1")
public class StatusController {

    /** Wird von der App geprueft, bevor sie sich anmeldet. */
    public static final int API_VERSION = 1;

    private final JdbcTemplate jdbc;
    private final Produktsuche produktsuche;

    public StatusController(JdbcTemplate jdbc, Produktsuche produktsuche) {
        this.jdbc = jdbc;
        this.produktsuche = produktsuche;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> ergebnis = new HashMap<>();
        ergebnis.put("anwendung", "TagStock-Server");
        ergebnis.put("apiVersion", API_VERSION);
        ergebnis.put("bereit", true);
        Integer konten = jdbc.queryForObject("SELECT COUNT(*) FROM benutzer", Integer.class);
        // Ohne Konten zeigt die App den Hinweis, das erste Konto anzulegen.
        ergebnis.put("eingerichtet", konten != null && konten > 0);
        // Damit die App das Nachschlagen gar nicht erst anbietet, wenn es fehlt.
        ergebnis.put("gtinDienst", produktsuche.eingeschaltet());
        return ergebnis;
    }
}
