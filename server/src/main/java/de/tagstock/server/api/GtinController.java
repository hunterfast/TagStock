package de.tagstock.server.api;

import de.tagstock.server.dienst.Produktsuche;
import de.tagstock.server.modell.Produktinfo;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nachschlagen einer GTIN. Die App fragt hier an, nicht direkt beim Anbieter -
 * so bleibt ein etwaiger Zugangsschluessel auf dem Server, und jede Nummer geht
 * nur einmal nach draussen.
 */
@RestController
@RequestMapping("/api/v1/gtin")
public class GtinController {

    private final Produktsuche suche;
    private final Zugriff zugriff;

    public GtinController(Produktsuche suche, Zugriff zugriff) {
        this.suche = suche;
        this.zugriff = zugriff;
    }

    @GetMapping("/{gtin}")
    public Produktinfo nachschlagen(HttpServletRequest anfrage, @PathVariable String gtin) {
        zugriff.benutzer(anfrage);
        String nummer = gtin == null ? "" : gtin.trim();
        if (!nummer.matches("\\d{8}|\\d{12,14}")) {
            throw ApiFehler.ungueltig("Keine gültige GTIN");
        }
        if (!suche.eingeschaltet()) {
            throw new ApiFehler(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Auf diesem Server ist kein Nachschlagedienst eingerichtet");
        }
        return suche.suchen(nummer);
    }
}
