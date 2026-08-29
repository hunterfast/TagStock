package de.tagstock.util;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.format.DateFormat;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import java.util.Date;

import de.tagstock.R;
import de.tagstock.data.CodeType;
import de.tagstock.data.Item;
import de.tagstock.data.ItemStatus;

/** Kleine Helfer zur Anzeige von Datum, Status und Codes. */
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

    /** Zeile "QR-Code · 4006381333931" oder null, wenn kein Code hinterlegt ist. */
    public static String codeLine(Context context, Item item) {
        if (item.code == null || item.code.isEmpty()) {
            return null;
        }
        CodeType type = item.codeType == null ? CodeType.KEINER : item.codeType;
        return context.getString(type.labelRes) + " · " + item.code;
    }

    /** Zeile mit Menge und - falls verliehen - Ausleiher und Datum. */
    public static String detailLine(Context context, Item item) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.artikel_menge_kurz, item.menge));
        if (item.status == ItemStatus.VERLIEHEN && item.verliehenAn != null && !item.verliehenAn.isEmpty()) {
            sb.append(" · ").append(item.verliehenAn);
            if (item.verliehenSeit != null) {
                sb.append(" (").append(context.getString(
                        R.string.artikel_verliehen_seit, date(context, item.verliehenSeit))).append(")");
            }
        } else if (item.beschreibung != null && !item.beschreibung.isEmpty()) {
            sb.append(" · ").append(item.beschreibung);
        }
        return sb.toString();
    }
}
