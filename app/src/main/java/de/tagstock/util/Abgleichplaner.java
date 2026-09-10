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
    private static final String REGELMAESSIG = "tagstock-abgleich-regelmaessig";

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

    /** Sicherheitsnetz: auch ohne Aenderung regelmaessig nach Neuem sehen. */
    public static void regelmaessigPlanen(Context context) {
        if (!Einstellungen.serverAktiv(context)) {
            return;
        }
        PeriodicWorkRequest auftrag = new PeriodicWorkRequest.Builder(
                AbgleichArbeit.class, 3, TimeUnit.HOURS)
                .setConstraints(mitNetz())
                .build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                REGELMAESSIG, ExistingPeriodicWorkPolicy.KEEP, auftrag);
    }

    /** Beim Abmelden: nichts mehr im Hintergrund versuchen. */
    public static void abbrechen(Context context) {
        WorkManager verwaltung = WorkManager.getInstance(context.getApplicationContext());
        verwaltung.cancelUniqueWork(EINMALIG);
        verwaltung.cancelUniqueWork(REGELMAESSIG);
    }
}
