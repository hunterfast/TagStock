package de.tagstock.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fuehrt kurze Aufgaben abseits des UI-Threads aus (Datei lesen, JSON bauen). */
public final class Hintergrund {

    public interface Fertig<T> {
        void onFertig(T ergebnis);
    }

    public interface Fehlgeschlagen {
        void onFehler(Exception fehler);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Hintergrund() {
    }

    public static <T> void starte(Callable<T> arbeit, Fertig<T> fertig, Fehlgeschlagen fehler) {
        EXECUTOR.execute(() -> {
            try {
                T ergebnis = arbeit.call();
                MAIN.post(() -> fertig.onFertig(ergebnis));
            } catch (Exception e) {
                MAIN.post(() -> fehler.onFehler(e));
            }
        });
    }
}
