package de.tagstock.util;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import de.tagstock.data.AbgleichArbeit;

/**
 * Merkt vor, dass etwas zum Server soll. Ohne Verbindung passiert nichts
 * weiter - Android startet die Uebertragung von selbst, sobald wieder Netz da
 * ist, auch wenn die App inzwischen geschlossen wurde.
 */
public final class Abgleichplaner {

    private static final String EINMALIG = "tagstock-abgleich";
    private static final String NETZ = "tagstock-abgleich-netz";
    private static final String REGELMAESSIG = "tagstock-abgleich-regelmaessig";
    /** Wie oft im Hintergrund nachgesehen wird, wenn die App zu ist. */
    private static final long TAKT_STUNDEN = 1;

    private Abgleichplaner() {
    }

    private static Constraints mitNetz() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    /** Nach einer Aenderung aufrufen. Ohne Server eingerichtet passiert nichts. */
    public static void vormerken(Context context) {
        if (!Einstellungen.serverAktiv(context)) {
            return;
        }
        OneTimeWorkRequest auftrag = new OneTimeWorkRequest.Builder(AbgleichArbeit.class)
                .setConstraints(mitNetz())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        // APPEND_OR_REPLACE waere unnoetig: ein laufender Abgleich nimmt alles mit.
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(EINMALIG, ExistingWorkPolicy.KEEP, auftrag);
    }

    /**
     * Ein anderes Netz ist da - WLAN, Mobilfunk oder VPN. Ob der Server
     * darueber erreichbar ist, zeigt nur ein Versuch. Der kommt gleich und
     * ersetzt einen, der womoeglich noch auf seine Wiederholung wartet.
     */
    public static void netzDa(Context context) {
        if (!Einstellungen.serverAktiv(context)) {
            return;
        }
        OneTimeWorkRequest auftrag = new OneTimeWorkRequest.Builder(AbgleichArbeit.class)
                .setConstraints(mitNetz())
                // Ein frisch aufgebautes Netz braucht einen Moment.
                .setInitialDelay(3, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(NETZ, ExistingWorkPolicy.REPLACE, auftrag);
    }

    /**
     * Auch ohne Aenderung regelmaessig nach Neuem sehen, wenn die App zu ist -
     * Android weckt sie bei einem neuen Netz nicht.
     */
    public static void regelmaessigPlanen(Context context) {
        if (!Einstellungen.serverAktiv(context)) {
            return;
        }
        PeriodicWorkRequest auftrag = new PeriodicWorkRequest.Builder(
                AbgleichArbeit.class, TAKT_STUNDEN, TimeUnit.HOURS)
                .setConstraints(mitNetz())
                .build();
        // UPDATE statt KEEP: Wer von einer Fassung mit anderem Takt kommt,
        // bekommt den neuen.
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                REGELMAESSIG, ExistingPeriodicWorkPolicy.UPDATE, auftrag);
    }

    /** Beim Abmelden: nichts mehr im Hintergrund versuchen. */
    public static void abbrechen(Context context) {
        WorkManager verwaltung = WorkManager.getInstance(context.getApplicationContext());
        verwaltung.cancelUniqueWork(EINMALIG);
        verwaltung.cancelUniqueWork(NETZ);
        verwaltung.cancelUniqueWork(REGELMAESSIG);
    }
}
