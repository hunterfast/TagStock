package de.tagstock.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.tagstock.data.Abgleich;

/**
 * Haelt die Geraete auf demselben Stand, solange die App zu sehen ist: sofort,
 * wenn sie nach vorn kommt, danach alle 30 Sekunden, und gleich, wenn ein
 * Netz dazukommt - WLAN oder VPN. Ist sie zu, uebernimmt der stuendliche
 * Auftrag im Hintergrund ({@link Abgleichplaner}).
 *
 * <p>Jeder Durchgang ist eine kleine Anfrage: Was hier offen ist, geht hoch,
 * was sich seit dem letzten Mal auf dem Server getan hat, kommt herunter.
 * Ist der Server nicht erreichbar, passiert nichts weiter - der naechste Takt
 * versucht es wieder.</p>
 */
public final class Dauerabgleich implements Application.ActivityLifecycleCallbacks {

    private static final long TAKT_MS = 30_000L;
    /** Ein frisch aufgebautes Netz braucht einen Moment, bis der Server darueber erreichbar ist. */
    private static final long NETZ_VORLAUF_MS = 3_000L;
    /** Drehen des Bildschirms startet die Activity neu - das ist kein Grund fuer einen Abgleich. */
    private static final long MINDESTABSTAND_MS = 2_000L;

    @Nullable
    private static volatile Dauerabgleich instanz;

    private final Context context;
    private final Handler haupt = new Handler(Looper.getMainLooper());
    /** Wie viele Activities gerade zu sehen sind; nur im Hauptthread geaendert. */
    private volatile int sichtbar;
    private long zuletzt = -MINDESTABSTAND_MS;

    private final Runnable takt = new Runnable() {
        @Override
        public void run() {
            abgleichen();
            haupt.postDelayed(this, TAKT_MS);
        }
    };

    private Dauerabgleich(Context context) {
        this.context = context.getApplicationContext();
    }

    public static void anmelden(Application app) {
        Dauerabgleich neu = new Dauerabgleich(app);
        instanz = neu;
        app.registerActivityLifecycleCallbacks(neu);
    }

    /**
     * Ein Netz ist dazugekommen. Gibt true zurueck, wenn die App offen ist und
     * den Abgleich selbst anstoesst - dann braucht es keinen Hintergrundauftrag.
     */
    public static boolean netzDa() {
        Dauerabgleich dauer = instanz;
        if (dauer == null || dauer.sichtbar == 0) {
            return false;
        }
        dauer.haupt.post(() -> {
            if (dauer.sichtbar > 0) {
                dauer.haupt.removeCallbacks(dauer.takt);
                dauer.haupt.postDelayed(dauer.takt, NETZ_VORLAUF_MS);
            }
        });
        return true;
    }

    private void abgleichen() {
        if (!Einstellungen.serverAktiv(context) || Einstellungen.teamId(context) == null) {
            return;
        }
        long jetzt = SystemClock.elapsedRealtime();
        if (jetzt - zuletzt < MINDESTABSTAND_MS) {
            return;
        }
        zuletzt = jetzt;
        // Das Ergebnis zeigt die Liste von selbst, sie haengt an der Datenbank.
        Abgleich.nebenbei(context);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (sichtbar++ == 0) {
            haupt.removeCallbacks(takt);
            haupt.post(takt);
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (sichtbar > 0 && --sichtbar == 0) {
            haupt.removeCallbacks(takt);
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle zustand) {
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle zustand) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}
