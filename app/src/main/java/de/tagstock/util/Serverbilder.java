package de.tagstock.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Artikelbilder liegen auf dem Server. Auf dem Geraet bleibt nur ein
 * Zwischenspeicher, damit die Liste ohne Netz etwas zu zeigen hat; Android darf
 * ihn jederzeit leeren, das Bild kommt dann beim naechsten Anzeigen wieder.
 */
public final class Serverbilder {

    /** Kantenlaenge, auf die eine Aufnahme vor dem Hochladen verkleinert wird. */
    private static final int MAX_KANTE = 1600;
    private static final int QUALITAET = 85;
    private static final String ORDNER = "serverbilder";

    private Serverbilder() {
    }

    private static File verzeichnis(Context context) {
        File dir = new File(context.getCacheDir(), ORDNER);
        if (!dir.exists() && !dir.mkdirs()) {
            return context.getCacheDir();
        }
        return dir;
    }

    /** Dateiname im Zwischenspeicher - aus der Adresse abgeleitet, damit er eindeutig ist. */
    public static File cacheDatei(Context context, String bildUrl) {
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < bildUrl.length(); i++) {
            char zeichen = bildUrl.charAt(i);
            boolean erlaubt = (zeichen >= 'a' && zeichen <= 'z')
                    || (zeichen >= 'A' && zeichen <= 'Z')
                    || (zeichen >= '0' && zeichen <= '9') || zeichen == '-';
            name.append(erlaubt ? zeichen : '_');
        }
        return new File(verzeichnis(context), name.toString());
    }

    /** Holt das Bild vom Server, falls es noch nicht im Zwischenspeicher liegt. */
    @Nullable
    public static File sicherstellen(Context context, @Nullable String bildUrl) {
        if (bildUrl == null || bildUrl.isEmpty()) {
            return null;
        }
        File ziel = cacheDatei(context, bildUrl);
        if (ziel.exists() && ziel.length() > 0) {
            return ziel;
        }
        String url = Einstellungen.serverUrl(context);
        String token = Einstellungen.token(context);
        if (url == null || token == null) {
            return null;
        }
        try {
            byte[] daten = new ServerClient(url, token).bildHolen(bildUrl);
            if (daten == null || daten.length == 0) {
                return null;
            }
            schreiben(ziel, daten);
            return ziel;
        } catch (IOException fehler) {
            return null;
        }
    }

    /** Legt eine bereits hochgeladene Aufnahme unter ihrer Serveradresse ab. */
    public static void uebernehmen(Context context, byte[] daten, String bildUrl) {
        try {
            schreiben(cacheDatei(context, bildUrl), daten);
        } catch (IOException ignoriert) {
            // Ohne Zwischenspeicher wird das Bild eben neu geladen.
        }
    }

    public static void entfernen(Context context, @Nullable String bildUrl) {
        if (bildUrl == null || bildUrl.isEmpty()) {
            return;
        }
        File datei = cacheDatei(context, bildUrl);
        if (datei.exists()) {
            //noinspection ResultOfMethodCallIgnored
            datei.delete();
        }
    }

    /**
     * Bereitet eine Aufnahme fuers Hochladen auf: verkleinert und als JPEG, damit
     * aus zehn Megabyte Kameradatei ein paar hundert Kilobyte werden.
     */
    @Nullable
    public static byte[] zumHochladen(Context context, @Nullable String fotoName) {
        Bitmap bitmap = Fotos.laden(context, fotoName, MAX_KANTE);
        if (bitmap == null) {
            return null;
        }
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITAET, aus);
        bitmap.recycle();
        return aus.toByteArray();
    }

    /** Liest ein Bild aus dem Zwischenspeicher, verkleinert fuer die Anzeige. */
    @Nullable
    public static Bitmap anzeigen(Context context, String bildUrl, int maxKante) {
        File datei = sicherstellen(context, bildUrl);
        if (datei == null) {
            return null;
        }
        return Fotos.ausDatei(datei, maxKante);
    }

    private static void schreiben(File ziel, byte[] daten) throws IOException {
        File vorlaeufig = new File(ziel.getAbsolutePath() + ".teil");
        try (OutputStream aus = new FileOutputStream(vorlaeufig)) {
            aus.write(daten);
        }
        if (!vorlaeufig.renameTo(ziel)) {
            //noinspection ResultOfMethodCallIgnored
            vorlaeufig.delete();
            throw new IOException("Zwischenspeicher nicht beschreibbar");
        }
    }
}
