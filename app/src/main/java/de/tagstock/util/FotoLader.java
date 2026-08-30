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

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private FotoLader() {
    }

    /**
     * Zeigt das Foto an oder das Ersatzsymbol, wenn keines hinterlegt ist.
     * Recycelte Views werden ueber das Tag abgesichert.
     */
    public static void laden(ImageView view, @Nullable String name, int ersatzRes) {
        view.setTag(name);
        if (name == null || name.isEmpty()) {
            view.setImageResource(ersatzRes);
            return;
        }
        Bitmap gecacht = CACHE.get(name);
        if (gecacht != null) {
            view.setImageBitmap(gecacht);
            return;
        }
        view.setImageResource(ersatzRes);
        Context context = view.getContext().getApplicationContext();
        EXECUTOR.execute(() -> {
            Bitmap bitmap = Fotos.laden(context, name, MAX_KANTE_PX);
            if (bitmap == null) {
                return;
            }
            CACHE.put(name, bitmap);
            MAIN.post(() -> {
                if (name.equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    /** Nach dem Austauschen eines Fotos den alten Eintrag verwerfen. */
    public static void vergessen(@Nullable String name) {
        if (name != null) {
            CACHE.remove(name);
        }
    }
}
