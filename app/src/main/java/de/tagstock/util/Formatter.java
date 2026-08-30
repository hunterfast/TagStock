package de.tagstock.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateFormat;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import java.util.Date;

import de.tagstock.R;
import de.tagstock.data.Code;
import de.tagstock.data.ItemStatus;
import de.tagstock.data.ItemWithState;
import de.tagstock.data.Verleih;

/** Kleine Helfer zur Anzeige von Datum, Bestand und Codes. */
public final class Formatter {

    private Formatter() {
    }

    public static String date(Context context, long millis) {
        return DateFormat.getDateFormat(context).format(new Date(millis));
    }

    public static String statusLabel(Context context, ItemStatus status) {
        return context.getString(status.labelRes);
    }

    /** Faerbt die Status-Plakette eines Listeneintrags. */
    public static void bindStatusPill(TextView view, ItemStatus status) {
        Context context = view.getContext();
        view.setText(statusLabel(context, status));
        ViewCompat.setBackgroundTintList(view,
                ColorStateList.valueOf(ContextCompat.getColor(context, status.colorRes)));
    }

    /** "3 vorhanden · 2 verliehen · 1 verloren" - nur belegte Zustaende. */
    public static String bestandText(Context context, ItemWithState state) {
        StringBuilder text = new StringBuilder();
        anhaengen(context, text, state.vorhanden(), R.string.bestand_vorhanden);
        anhaengen(context, text, state.verliehen, R.string.bestand_verliehen);
        anhaengen(context, text, state.verloren(), R.string.bestand_verloren);
        if (text.length() == 0) {
            return context.getString(R.string.bestand_leer);
        }
        return text.toString();
    }

    private static void anhaengen(Context context, StringBuilder text, int anzahl, int formatRes) {
        if (anzahl <= 0) {
            return;
        }
        if (text.length() > 0) {
            text.append(" · ");
        }
        text.append(context.getString(formatRes, anzahl));
    }

    /** "QR-Code · 4006381333931" plus Hinweis auf weitere Codes, sonst null. */
    @Nullable
    public static String codeText(Context context, ItemWithState state) {
        Code erster = state.ersterCode();
        if (erster == null) {
            return null;
        }
        String text = context.getString(erster.typ.labelRes) + " · " + erster.wert;
        int weitere = state.codes.size() - 1;
        if (weitere > 0) {
            text += " " + context.getResources().getQuantityString(
                    R.plurals.code_weitere, weitere, weitere);
        }
        return text;
    }

    /** "Max Mustermann · 2 Stueck · seit 12.03.2026" bzw. mit Rueckgabedatum. */
    public static String verleihZeile(Context context, Verleih verleih) {
        StringBuilder text = new StringBuilder(verleih.person);
        if (verleih.menge > 1) {
            text.append(" · ").append(context.getString(R.string.verleih_stueck, verleih.menge));
        }
        text.append(" · ");
        if (verleih.istOffen()) {
            text.append(context.getString(R.string.verleih_seit,
                    date(context, verleih.ausgeliehenAm)));
        } else {
            text.append(context.getString(R.string.verleih_zurueck_am,
                    date(context, verleih.ausgeliehenAm),
                    date(context, verleih.zurueckAm)));
        }
        return text.toString();
    }
}
