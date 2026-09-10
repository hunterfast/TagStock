package de.tagstock.server.api;

import de.tagstock.server.dienst.Aktualisierungswache;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Welcher Stand laeuft hier, und gibt es einen neueren? */
@RestController
@RequestMapping("/api/v1/aktualisierung")
public class AktualisierungController {

    private final Aktualisierungswache wache;
    private final Zugriff zugriff;

    public AktualisierungController(Aktualisierungswache wache, Zugriff zugriff) {
        this.wache = wache;
        this.zugriff = zugriff;
    }

    @GetMapping
    public Map<String, Object> stand(HttpServletRequest anfrage) {
        zugriff.benutzer(anfrage);
        return wache.stand();
    }

    /** Sofort noch einmal nachsehen, statt auf den taeglichen Takt zu warten. */
    @PostMapping("/pruefen")
    public Map<String, Object> jetztPruefen(HttpServletRequest anfrage) {
        zugriff.benutzer(anfrage);
        wache.vergessen();
        return wache.stand();
    }
}
