package de.tagstock.server.api;

import de.tagstock.server.dienst.Aktualisierungswache;
import de.tagstock.server.dienst.Selbstpflege;

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
    private final Selbstpflege pflege;
    private final Zugriff zugriff;

    public AktualisierungController(Aktualisierungswache wache, Selbstpflege pflege,
                                    Zugriff zugriff) {
        this.wache = wache;
        this.pflege = pflege;
        this.zugriff = zugriff;
    }

    @GetMapping
    public Map<String, Object> stand(HttpServletRequest anfrage) {
        zugriff.benutzer(anfrage);
        Map<String, Object> ergebnis = new java.util.LinkedHashMap<>(wache.stand());
        ergebnis.putAll(pflege.stand(false));
        return ergebnis;
    }

    /** Sofort noch einmal nachsehen, statt auf den taeglichen Takt zu warten. */
    @PostMapping("/pruefen")
    public Map<String, Object> jetztPruefen(HttpServletRequest anfrage) {
        zugriff.benutzer(anfrage);
        wache.vergessen();
        Map<String, Object> ergebnis = new java.util.LinkedHashMap<>(wache.stand());
        ergebnis.putAll(pflege.stand(true));
        return ergebnis;
    }

    /**
     * Neueste Fassung holen und uebernehmen. Der Server startet dafuer neu -
     * die Antwort geht noch raus, danach ist er fuer ein paar Sekunden weg.
     */
    @PostMapping("/einspielen")
    public Map<String, Object> einspielen(HttpServletRequest anfrage) throws Exception {
        zugriff.verwalter(anfrage);
        return pflege.einspielen();
    }

    /** Zurueck auf die Fassung davor, ebenfalls mit Neustart. */
    @PostMapping("/zurueck")
    public Map<String, Object> zurueck(HttpServletRequest anfrage) throws Exception {
        zugriff.verwalter(anfrage);
        return pflege.zurueck();
    }
}
