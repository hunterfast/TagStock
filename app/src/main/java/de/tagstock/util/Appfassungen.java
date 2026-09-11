package de.tagstock.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Holt eine App-Fassung vom eigenen Server und uebergibt sie der
 * Installationsanwendung. Heruntergeladen wird nur nach Zustimmung, installiert
 * wird ohnehin erst nach dem Systemdialog - die App tut das nie von selbst.
 */
public final class Appfassungen {

    private static final String ORDNER = "fassungen";
    private static final int VERBINDUNG_MS = 8000;
    private static final int LESEN_MS = 120000;

    /** Was die Pruefung einer geladenen Datei ergeben hat. */
    public enum Befund {
        /** Laesst sich ueber die laufende Fassung installieren. */
        PASST,
        /** Die Datei ist keine lesbare App - meist ein abgebrochener Download. */
        UNLESBAR,
        /**
         * Die Datei ist mit einem anderen Schluessel signiert als die laufende
         * App. Android lehnt sie dann mit "App nicht installiert" ab, ohne zu
         * sagen warum.
         */
        ANDERE_SIGNATUR
    }

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
            long angekuendigt = verbindung.getContentLengthLong();
            long geschrieben = 0;
            try (InputStream ein = verbindung.getInputStream();
                 OutputStream aus = new FileOutputStream(teil)) {
                byte[] puffer = new byte[16384];
                int gelesen;
                while ((gelesen = ein.read(puffer)) != -1) {
                    aus.write(puffer, 0, gelesen);
                    geschrieben += gelesen;
                }
            }
            // Eine abgebrochene Uebertragung faellt sonst erst beim
            // Installieren auf - und dann nur als "App nicht installiert".
            if (angekuendigt > 0 && geschrieben != angekuendigt) {
                //noinspection ResultOfMethodCallIgnored
                teil.delete();
                throw new IOException("Die Uebertragung ist abgebrochen ("
                        + geschrieben + " von " + angekuendigt + " Byte)");
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

    /**
     * Sieht die geladene Datei durch, bevor sie dem System vorgelegt wird.
     * Andernfalls bleibt es beim wortkargen "App nicht installiert", und die
     * Ursache - abgebrochener Download oder anderer Signierschluessel - muss
     * man raten. Liest eine 30-MB-Datei und gehoert deshalb in den
     * Hintergrund.
     */
    public static Befund pruefen(Context context, File apk) {
        PackageManager pakete = context.getPackageManager();
        Set<String> neue = abdruecke(signaturen(
                pakete.getPackageArchiveInfo(apk.getAbsolutePath(), signaturflagge())));
        if (neue.isEmpty()) {
            // Entweder unlesbar oder das Geraet rueckt die Signatur nicht
            // heraus; ist die Datei lesbar, lassen wir sie durch.
            return pakete.getPackageArchiveInfo(apk.getAbsolutePath(), 0) == null
                    ? Befund.UNLESBAR : Befund.PASST;
        }
        Set<String> eigene;
        try {
            eigene = abdruecke(signaturen(
                    pakete.getPackageInfo(context.getPackageName(), signaturflagge())));
        } catch (PackageManager.NameNotFoundException ausnahme) {
            return Befund.PASST;
        }
        if (eigene.isEmpty()) {
            return Befund.PASST;
        }
        return Collections.disjoint(eigene, neue) ? Befund.ANDERE_SIGNATUR : Befund.PASST;
    }

    /** Datei wegwerfen, damit der naechste Versuch wirklich neu laedt. */
    public static void wegwerfen(File apk) {
        //noinspection ResultOfMethodCallIgnored
        apk.delete();
    }

    @SuppressWarnings("deprecation")
    private static int signaturflagge() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private static Signature[] signaturen(@Nullable PackageInfo angaben) {
        if (angaben == null) {
            return null;
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? neueSignaturen(angaben) : angaben.signatures;
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @Nullable
    private static Signature[] neueSignaturen(PackageInfo angaben) {
        SigningInfo unterschrift = angaben.signingInfo;
        if (unterschrift == null) {
            return null;
        }
        // Bei einem Schluesselwechsel zaehlt jede Fassung der Kette.
        return unterschrift.hasMultipleSigners()
                ? unterschrift.getApkContentsSigners()
                : unterschrift.getSigningCertificateHistory();
    }

    private static Set<String> abdruecke(@Nullable Signature[] signaturen) {
        Set<String> menge = new HashSet<>();
        if (signaturen == null) {
            return menge;
        }
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ausnahme) {
            return menge;
        }
        for (Signature signatur : signaturen) {
            if (signatur == null) {
                continue;
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : sha.digest(signatur.toByteArray())) {
                hex.append(String.format(Locale.ROOT, "%02X", b));
            }
            menge.add(hex.toString());
        }
        return menge;
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
