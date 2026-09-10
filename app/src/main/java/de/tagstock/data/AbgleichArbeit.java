package de.tagstock.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import de.tagstock.util.Einstellungen;

/**
 * Traegt offene Aenderungen zum Server. Wird von Android gestartet, sobald eine
 * Verbindung besteht - auch wenn die App geschlossen ist. Geht etwas schief,
 * versucht WorkManager es spaeter mit wachsendem Abstand erneut.
 */
public class AbgleichArbeit extends Worker {

    public AbgleichArbeit(@NonNull Context context, @NonNull WorkerParameters parameter) {
        super(context, parameter);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!Einstellungen.serverAktiv(context) || Einstellungen.teamId(context) == null) {
            return Result.success();
        }
        Abgleich.Ergebnis ergebnis = Abgleich.jetzt(context);
        if (ergebnis.erfolgreich() || ergebnis.laeuftSchon) {
            return Result.success();
        }
        if (ergebnis.abgemeldet) {
            // Ein abgelaufener Token wird durch Wiederholen nicht besser.
            return Result.failure();
        }
        return Result.retry();
    }
}
