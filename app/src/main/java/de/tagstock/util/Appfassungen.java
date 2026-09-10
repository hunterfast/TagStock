package de.tagstock.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Holt eine App-Fassung vom eigenen Server und uebergibt sie der
 * Installationsanwendung. Heruntergeladen wird nur nach Zustimmung, installiert
 * wird ohnehin erst nach dem Systemdialog - die App tut das nie von selbst.
 */
public final class Appfassungen {

    private static final String ORDNER = "fassungen";
    private static final int VERBINDUNG_MS = 8000;
    private static final int LESEN_MS = 120000;

    private Appfassungen() {
    }

    private static File verzeichnis(Context context) {
        File ordner = new File(context.getFilesDir(), ORDNER);
        //noinspection ResultOfMethodCallIgnored
        ordner.mkdirs();
        return ordner;
    }

    /**
     * Laedt die Datei herunter und gibt sie zurueck. Blockiert und gehoert
     * deshalb in einen Hintergrund-Thread.
     */
    public static File herunterladen(Context context, String adresse, int versionCode)
            throws IOException {
        File ziel = new File(verzeichnis(context), "tagstock-" + versionCode + ".apk");
        if (ziel.exists() && ziel.length() > 0) {
            return ziel;
        }
        File teil = new File(ziel.getAbsolutePath() + ".teil");
        HttpURLConnection verbindung = (HttpURLConnection) new URL(adresse).openConnection();
        try {
            verbindung.setConnectTimeout(VERBINDUNG_MS);
            verbindung.setReadTimeout(LESEN_MS);
            int status = verbindung.getResponseCode();
            if (status >= 400) {
                throw new IOException("Server meldet Fehler " + status);
            }
            try (InputStream ein = verbindung.getInputStream();
                 OutputStream aus = new FileOutputStream(teil)) {
                byte[] puffer = new byte[16384];
                int gelesen;
                while ((gelesen = ein.read(puffer)) != -1) {
                    aus.write(puffer, 0, gelesen);
                }
            }
        } finally {
            verbindung.disconnect();
        }
        if (teil.length() < 1024) {
            //noinspection ResultOfMethodCallIgnored
            teil.delete();
            throw new IOException("Die heruntergeladene Datei ist unbrauchbar");
        }
        if (!teil.renameTo(ziel)) {
            throw new IOException("Datei konnte nicht abgelegt werden");
        }
        aufraeumen(context, ziel);
        return ziel;
    }

    /** Alte Downloads wegwerfen, damit nicht jede Fassung liegen bleibt. */
    private static void aufraeumen(Context context, File behalten) {
        File[] dateien = verzeichnis(context).listFiles();
        if (dateien == null) {
            return;
        }
        for (File datei : dateien) {
            if (!datei.equals(behalten)) {
                //noinspection ResultOfMethodCallIgnored
                datei.delete();
            }
        }
    }

    /** Uebergibt die Datei dem System; den Rest entscheidet die Person am Geraet. */
    @Nullable
    public static Intent installieren(Context context, File apk) {
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fotos", apk);
        Intent absicht = new Intent(Intent.ACTION_VIEW);
        absicht.setDataAndType(uri, "application/vnd.android.package-archive");
        absicht.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return absicht.resolveActivity(context.getPackageManager()) == null ? null : absicht;
    }
}
