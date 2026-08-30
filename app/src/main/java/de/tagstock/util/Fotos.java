package de.tagstock.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/** Artikelfotos im privaten App-Verzeichnis. */
public final class Fotos {

    private static final String ORDNER = "fotos";

    private Fotos() {
    }

    private static File verzeichnis(Context context) {
        File dir = new File(context.getFilesDir(), ORDNER);
        if (!dir.exists() && !dir.mkdirs()) {
            return context.getFilesDir();
        }
        return dir;
    }

    public static File datei(Context context, String name) {
        return new File(verzeichnis(context), name);
    }

    /** Legt eine leere Datei fuer eine neue Aufnahme an und gibt ihren Namen zurueck. */
    public static String neuerName() {
        return "foto_" + UUID.randomUUID() + ".jpg";
    }

    /** Content-URI, die die Kamera-App beschreiben darf. */
    public static Uri uriFuer(Context context, String name) throws IOException {
        File file = datei(context, name);
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Datei konnte nicht angelegt werden");
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fotos", file);
    }

    /** Uebernimmt ein Bild aus der Galerie in das App-Verzeichnis. */
    @Nullable
    public static String uebernehmen(Context context, Uri quelle) {
        String name = neuerName();
        try (InputStream in = context.getContentResolver().openInputStream(quelle);
             OutputStream out = new FileOutputStream(datei(context, name))) {
            if (in == null) {
                return null;
            }
            byte[] puffer = new byte[8192];
            int gelesen;
            while ((gelesen = in.read(puffer)) != -1) {
                out.write(puffer, 0, gelesen);
            }
            return name;
        } catch (IOException e) {
            return null;
        }
    }

    /** Laedt ein Foto verkleinert, damit grosse Aufnahmen den Speicher nicht sprengen. */
    @Nullable
    public static Bitmap laden(Context context, @Nullable String name, int maxKante) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        File file = datei(context, name);
        if (!file.exists() || file.length() == 0) {
            return null;
        }
        BitmapFactory.Options masse = new BitmapFactory.Options();
        masse.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), masse);

        int faktor = 1;
        int groesste = Math.max(masse.outWidth, masse.outHeight);
        while (groesste / faktor > maxKante) {
            faktor *= 2;
        }

        BitmapFactory.Options optionen = new BitmapFactory.Options();
        optionen.inSampleSize = faktor;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), optionen);
    }

    public static boolean existiert(Context context, @Nullable String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        File file = datei(context, name);
        return file.exists() && file.length() > 0;
    }

    public static void loeschen(Context context, @Nullable String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        File file = datei(context, name);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
