package de.tagstock.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Laedt Artikelfotos im Hintergrund in eine ImageView und haelt die zuletzt
 * benutzten im Speicher, damit das Scrollen in der Liste ruhig bleibt.
 */
public final class FotoLader {

    private static final int MAX_KANTE_PX = 240;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(40) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return 1;
        }
    };

    // Mehrere Threads, damit ein langsamer Serverabruf die Liste nicht aufhaelt.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private FotoLader() {
    }

    /**
     * Zeigt das Bild eines Artikels an oder das Ersatzsymbol, wenn keines
     * hinterlegt ist. Liegt das Bild auf dem Server, wird es beim ersten Mal
     * geholt und danach aus dem Zwischenspeicher genommen. Recycelte Views
     * werden ueber das Tag abgesichert.
     */
    public static void laden(ImageView view, @Nullable String fotoPfad, @Nullable String bildUrl,
                             int ersatzRes) {
        boolean vomServer = bildUrl != null && !bildUrl.isEmpty();
        String schluessel = vomServer ? bildUrl : fotoPfad;
        view.setTag(schluessel);
        if (schluessel == null || schluessel.isEmpty()) {
            view.setImageResource(ersatzRes);
            return;
        }
        Bitmap gecacht = CACHE.get(schluessel);
        if (gecacht != null) {
            view.setImageBitmap(gecacht);
            return;
        }
        view.setImageResource(ersatzRes);
        Context context = view.getContext().getApplicationContext();
        EXECUTOR.execute(() -> {
            Bitmap bitmap = vomServer
                    ? Serverbilder.anzeigen(context, schluessel, MAX_KANTE_PX)
                    : Fotos.laden(context, schluessel, MAX_KANTE_PX);
            if (bitmap == null) {
                return;
            }
            CACHE.put(schluessel, bitmap);
            MAIN.post(() -> {
                if (schluessel.equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    /** Kurzform fuer Bilder, die nur auf dem Geraet liegen. */
    public static void laden(ImageView view, @Nullable String fotoPfad, int ersatzRes) {
        laden(view, fotoPfad, null, ersatzRes);
    }

    /** Nach dem Austauschen eines Fotos den alten Eintrag verwerfen. */
    public static void vergessen(@Nullable String name) {
        if (name != null) {
            CACHE.remove(name);
        }
    }
}
