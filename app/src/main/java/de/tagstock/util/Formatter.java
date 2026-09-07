package de.tagstock.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import java.util.Calendar;
import java.util.Date;

import de.tagstock.R;
import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;

/** Anzeige-Helfer fuer Datum, Status und Artikelzeilen. */
public final class Formatter {

    private Formatter() {
    }

    public static String datum(Context context, long millis) {
        return DateFormat.getDateFormat(context).format(new Date(millis));
    }

    public static String datumZeit(Context context, long millis) {
        return DateFormat.getDateFormat(context).format(new Date(millis))
                + ", " + DateFormat.getTimeFormat(context).format(new Date(millis));
    }

    /** "vor 3 Monaten" - fuer den letzten Scan. */
    public static CharSequence seit(long millis) {
        return DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS);
    }

    /** Tagesbeginn - Rueckgabedaten werden ohne Uhrzeit gespeichert. */
    public static long tagesbeginn(long millis) {
        Calendar kalender = Calendar.getInstance();
        kalender.setTimeInMillis(millis);
        kalender.set(Calendar.HOUR_OF_DAY, 0);
        kalender.set(Calendar.MINUTE, 0);
        kalender.set(Calendar.SECOND, 0);
        kalender.set(Calendar.MILLISECOND, 0);
        return kalender.getTimeInMillis();
    }

    public static String statusLabel(Context context, ArtikelStatus status) {
        return context.getString(status.labelRes);
    }

    /** Faerbt die Status-Plakette eines Listeneintrags. */
    public static void statusPlakette(TextView view, ArtikelStatus status) {
        Context context = view.getContext();
        view.setText(statusLabel(context, status));
        ViewCompat.setBackgroundTintList(view,
                ColorStateList.valueOf(ContextCompat.getColor(context, status.farbeRes)));
    }

    /**
     * Zweite Zeile der Artikelkarte: bei verliehenen Artikeln die Person,
     * sonst die Kategorie - dahinter jeweils der Lagerort.
     */
    public static String zeile(Context context, Artikel artikel) {
        StringBuilder text = new StringBuilder();
        if (artikel.status == ArtikelStatus.VERLIEHEN
                && artikel.verliehenAn != null && !artikel.verliehenAn.isEmpty()) {
            text.append(artikel.verliehenAn);
        } else if (artikel.kategorie != null && !artikel.kategorie.isEmpty()) {
            text.append(artikel.kategorie);
        }
        if (artikel.lagerort != null && !artikel.lagerort.isEmpty()) {
            anhaengen(text, artikel.lagerort);
        } else if (artikel.standort != null && !artikel.standort.isEmpty()) {
            anhaengen(text, artikel.standort);
        }
        if (artikel.status == ArtikelStatus.VERLIEHEN && artikel.rueckgabeDatum != null) {
            anhaengen(text, artikel.istUeberfaellig()
                    ? context.getString(R.string.artikel_ueberfaellig_seit,
                    datum(context, artikel.rueckgabeDatum))
                    : context.getString(R.string.artikel_bis, datum(context, artikel.rueckgabeDatum)));
        }
        return text.toString();
    }

    private static void anhaengen(StringBuilder text, String wert) {
        if (text.length() > 0) {
            text.append(" · ");
        }
        text.append(wert);
    }

    /** Tage, die eine Rueckgabe ueberfaellig ist. */
    public static int tageUeberfaellig(Artikel artikel) {
        if (artikel.rueckgabeDatum == null) {
            return 0;
        }
        long differenz = System.currentTimeMillis() - artikel.rueckgabeDatum;
        return (int) Math.max(0, differenz / DateUtils.DAY_IN_MILLIS);
    }

    @Nullable
    public static String leerZuNull(String wert) {
        return wert == null || wert.trim().isEmpty() ? null : wert.trim();
    }
}
