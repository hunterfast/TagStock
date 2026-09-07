package de.tagstock.server.daten;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Legt die Bilddateien im Bilderordner ab - im Docker-Abbild unterhalb des
 * Volumes, damit sie Aktualisierungen ueberleben. Der Dateiname kommt vom
 * Server, nicht vom Geraet; Angaben aus der Anfrage landen nie in einem Pfad.
 */
@Component
public class Bildspeicher {

    /** Nur diese Formate nimmt der Server an. */
    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";

    private final Path wurzel;
    private final long maxBytes;

    public Bildspeicher(@Value("${tagstock.bilder-ordner:/data/bilder}") String ordner,
                        @Value("${tagstock.bild-max-mb:8}") int maxMb) {
        this.wurzel = Paths.get(ordner).toAbsolutePath().normalize();
        this.maxBytes = (long) maxMb * 1024L * 1024L;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public long maxMb() {
        return maxBytes / (1024L * 1024L);
    }

    /**
     * Erkennt das Format am Inhalt. Was hier nicht als Bild durchgeht, kommt
     * gar nicht erst auf die Platte.
     */
    public static String typAusInhalt(byte[] daten) {
        if (daten.length > 3 && (daten[0] & 0xFF) == 0xFF && (daten[1] & 0xFF) == 0xD8
                && (daten[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (daten.length > 8 && (daten[0] & 0xFF) == 0x89 && daten[1] == 'P' && daten[2] == 'N'
                && daten[3] == 'G') {
            return PNG;
        }
        if (daten.length > 12 && daten[0] == 'R' && daten[1] == 'I' && daten[2] == 'F'
                && daten[3] == 'F' && daten[8] == 'W' && daten[9] == 'E' && daten[10] == 'B'
                && daten[11] == 'P') {
            return WEBP;
        }
        return null;
    }

    public static String endung(String typ) {
        switch (typ) {
            case PNG:
                return "png";
            case WEBP:
                return "webp";
            default:
                return "jpg";
        }
    }

    /** Schreibt den Inhalt und gibt den Pfad unterhalb des Bilderordners zurueck. */
    public String schreiben(String teamId, String bildId, String typ, byte[] daten) {
        String name = teamId + "/" + bildId + "." + endung(typ);
        Path ziel = wurzel.resolve(name).normalize();
        if (!ziel.startsWith(wurzel)) {
            throw new IllegalArgumentException("Pfad liegt ausserhalb des Bilderordners");
        }
        try {
            Files.createDirectories(ziel.getParent());
            Files.write(ziel, daten);
        } catch (IOException fehler) {
            throw new UncheckedIOException(fehler);
        }
        return name;
    }

    public byte[] lesen(String name) throws IOException {
        Path datei = datei(name);
        if (datei == null || !Files.isRegularFile(datei)) {
            return null;
        }
        return Files.readAllBytes(datei);
    }

    public void loeschen(String name) {
        Path datei = datei(name);
        if (datei == null) {
            return;
        }
        try {
            Files.deleteIfExists(datei);
        } catch (IOException ignoriert) {
            // Beim Aufraeumen ist eine fehlende Datei kein Grund abzubrechen.
        }
    }

    /** Liest den Koerper der Anfrage und bricht ab, sobald die Grenze reisst. */
    public byte[] einlesen(InputStream strom) throws IOException {
        byte[] puffer = new byte[8192];
        java.io.ByteArrayOutputStream aus = new java.io.ByteArrayOutputStream();
        int gelesen;
        while ((gelesen = strom.read(puffer)) != -1) {
            aus.write(puffer, 0, gelesen);
            if (aus.size() > maxBytes) {
                return null;
            }
        }
        return aus.toByteArray();
    }

    private Path datei(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Path datei = wurzel.resolve(name).normalize();
        return datei.startsWith(wurzel) ? datei : null;
    }
}
