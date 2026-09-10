package de.tagstock.server.api;

import de.tagstock.server.dienst.Appwache;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Verteilt die App im eigenen Netz. Die Geraete fragen hier nach, ob es etwas
 * Neueres gibt, und holen es sich von hier. Neben der aktuellen liegt immer
 * die vorherige Fassung bereit - falls mit der neuen etwas nicht stimmt.
 */
@RestController
@RequestMapping("/api/v1/app")
public class AppController {

    private final Appwache wache;
    private final Zugriff zugriff;

    public AppController(Appwache wache, Zugriff zugriff) {
        this.wache = wache;
        this.zugriff = zugriff;
    }

    /** Was liegt bereit - aktuell und vorher. */
    @GetMapping
    public Map<String, Object> auskunft() {
        return wache.nachsehen(false);
    }

    /** Sofort bei der Quelle nachsehen, statt auf den naechsten Takt zu warten. */
    @PostMapping("/pruefen")
    public Map<String, Object> pruefen(HttpServletRequest anfrage) {
        zugriff.benutzer(anfrage);
        return wache.nachsehen(true);
    }

    /**
     * Die Datei selbst - bewusst ohne Anmeldung: ein frisches Geraet hat noch
     * kein Konto und soll die App trotzdem installieren koennen.
     */
    @GetMapping("/{fach}/tagstock.apk")
    public ResponseEntity<Resource> herunterladen(@PathVariable String fach) {
        if (!Appwache.AKTUELL.equals(fach) && !Appwache.VORHER.equals(fach)) {
            throw ApiFehler.nichtGefunden("Unbekanntes Fach");
        }
        return ausliefern(wache.datei(fach));
    }

    /** Kurzform fuer die aktuelle Fassung. */
    @GetMapping("/tagstock.apk")
    public ResponseEntity<Resource> herunterladen() {
        return ausliefern(wache.datei(Appwache.AKTUELL));
    }

    private ResponseEntity<Resource> ausliefern(Path apk) {
        if (!Files.isRegularFile(apk)) {
            throw ApiFehler.nichtGefunden("Es liegt keine App auf dem Server");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tagstock.apk\"")
                .body(new FileSystemResource(apk));
    }
}
