package de.tagstock.server.api;

import de.tagstock.server.dienst.Selbstpflege;
import de.tagstock.server.dienst.Veroeffentlichungen;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Welcher Stand laeuft hier, und gibt es einen neueren? Gefragt wird bei
 * GitHub nur, wenn jemand hier anfragt - und ohne Knopfdruck hoechstens
 * einmal je Woche (siehe {@link Veroeffentlichungen}).
 */
@RestController
@RequestMapping("/api/v1/aktualisierung")
public class AktualisierungController {

    private final Selbstpflege pflege;
    private final Veroeffentlichungen quelle;
    private final Zugriff zugriff;

    public AktualisierungController(Selbstpflege pflege, Veroeffentlichungen quelle,
                                    Zugriff zugriff) {
        this.pflege = pflege;
        this.quelle = quelle;
        this.zugriff = zugriff;
    }

    @GetMapping
    public Map<String, Object> stand(HttpServletRequest anfrage) {
        zugriff.benutzer(anfrage);
        return ergaenzt(pflege.stand(false));
    }

    /** Sofort nachsehen, statt auf die naechste Woche zu warten. */
    @PostMapping("/pruefen")
    public Map<String, Object> jetztPruefen(HttpServletRequest anfrage) {
        zugriff.benutzer(anfrage);
        return ergaenzt(pflege.stand(true));
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

    /**
     * Was App und Oberflaeche ausserdem lesen: ob es etwas Neueres gibt
     * (unter dem Namen, den die App seit jeher kennt), ob der Server von
     * selbst nachsieht und wann zuletzt.
     */
    private Map<String, Object> ergaenzt(Map<String, Object> ergebnis) {
        ergebnis.put("aktualisierungVerfuegbar",
                Boolean.TRUE.equals(ergebnis.get("neuerVorhanden")));
        ergebnis.put("geprueft", quelle.automatisch());
        ergebnis.put("nachsehenTage", quelle.abstandTage());
        long geholt = quelle.geholtAm();
        if (geholt > 0) {
            ergebnis.put("nachgesehenAm", Instant.ofEpochMilli(geholt).toString());
        }
        return ergebnis;
    }
}
