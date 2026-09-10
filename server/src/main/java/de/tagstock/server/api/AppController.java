package de.tagstock.server.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Verteilt die App im eigenen Netz. Wer eine neue APK in den Ordner legt,
 * versorgt damit alle Geraete: die App fragt hier nach, meldet eine neuere
 * Fassung und laedt sie von hier - ohne Konto bei irgendwem.
 *
 * <p>Im Ordner liegen <code>tagstock.apk</code> und daneben freiwillig eine
 * <code>app.json</code> mit <code>versionCode</code>, <code>versionName</code>
 * und <code>hinweis</code>. Fehlt sie, gibt es die Datei trotzdem, nur ohne
 * Versionsangabe.</p>
 */
@RestController
@RequestMapping("/api/v1/app")
public class AppController {

    private static final String DATEI = "tagstock.apk";

    private final Path ordner;

    public AppController(@Value("${tagstock.app-ordner:/data/app}") String ordner) {
        this.ordner = Paths.get(ordner).toAbsolutePath().normalize();
    }

    @GetMapping
    public Map<String, Object> auskunft() throws IOException {
        Map<String, Object> ergebnis = new HashMap<>();
        Path apk = ordner.resolve(DATEI);
        boolean da = Files.isRegularFile(apk);
        ergebnis.put("verfuegbar", da);
        if (!da) {
            return ergebnis;
        }
        ergebnis.put("groesse", Files.size(apk));
        ergebnis.put("stand", Files.getLastModifiedTime(apk).toInstant().toString());
        ergebnis.put("adresse", "/api/v1/app/" + DATEI);

        Path angaben = ordner.resolve("app.json");
        if (Files.isRegularFile(angaben)) {
            String inhalt = new String(Files.readAllBytes(angaben), StandardCharsets.UTF_8);
            ergebnis.put("versionCode", zahl(inhalt, "versionCode"));
            String name = de.tagstock.server.dienst.Produktsuche.feldAusJson(inhalt, "versionName");
            if (name != null && !name.isEmpty()) {
                ergebnis.put("versionName", name);
            }
            String hinweis = de.tagstock.server.dienst.Produktsuche.feldAusJson(inhalt, "hinweis");
            if (hinweis != null && !hinweis.isEmpty()) {
                ergebnis.put("hinweis", hinweis);
            }
        }
        return ergebnis;
    }

    /**
     * Die Datei selbst - bewusst ohne Anmeldung, denn ein frisches Geraet hat
     * noch kein Konto und soll die App trotzdem installieren koennen.
     */
    @GetMapping("/" + DATEI)
    public ResponseEntity<Resource> herunterladen() {
        Path apk = ordner.resolve(DATEI);
        if (!Files.isRegularFile(apk)) {
            throw ApiFehler.nichtGefunden("Es liegt keine App auf dem Server");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + DATEI + "\"")
                .body(new FileSystemResource(apk));
    }

    /** Ganzzahl aus einer flachen JSON-Datei; ohne Bibliothek, ohne Umschweife. */
    private static int zahl(String json, String feld) {
        String suche = "\"" + feld + "\"";
        int start = json.indexOf(suche);
        if (start < 0) {
            return 0;
        }
        int doppelpunkt = json.indexOf(':', start + suche.length());
        if (doppelpunkt < 0) {
            return 0;
        }
        StringBuilder ziffern = new StringBuilder();
        for (int i = doppelpunkt + 1; i < json.length(); i++) {
            char zeichen = json.charAt(i);
            if (Character.isDigit(zeichen)) {
                ziffern.append(zeichen);
            } else if (ziffern.length() > 0) {
                break;
            } else if (zeichen != ' ' && zeichen != '"') {
                break;
            }
        }
        return ziffern.length() == 0 ? 0 : Integer.parseInt(ziffern.toString());
    }
}
