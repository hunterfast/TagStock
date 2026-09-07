package de.tagstock.data;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

import de.tagstock.R;

/** Bestandsstatus eines Artikels - ein Artikel hat immer genau einen. */
public enum ArtikelStatus {

    VORHANDEN("vorhanden", R.string.status_vorhanden, R.color.status_vorhanden),
    NICHT_VORHANDEN("nicht vorhanden", R.string.status_nicht_vorhanden, R.color.status_fehlt),
    VERLIEHEN("verliehen", R.string.status_verliehen, R.color.status_verliehen),
    VERBRAUCHT("verbraucht", R.string.status_verbraucht, R.color.status_verbraucht),
    AUSGELAGERT("ausgelagert", R.string.status_ausgelagert, R.color.status_ausgelagert);

    /** Schreibweise wie im Server und in der Sicherung. */
    public final String schluessel;

    @StringRes
    public final int labelRes;

    @ColorRes
    public final int farbeRes;

    ArtikelStatus(String schluessel, @StringRes int labelRes, @ColorRes int farbeRes) {
        this.schluessel = schluessel;
        this.labelRes = labelRes;
        this.farbeRes = farbeRes;
    }

    public static ArtikelStatus vonSchluessel(String schluessel) {
        if (schluessel != null) {
            for (ArtikelStatus status : values()) {
                if (status.schluessel.equals(schluessel) || status.name().equals(schluessel)) {
                    return status;
                }
            }
        }
        return VORHANDEN;
    }
}
