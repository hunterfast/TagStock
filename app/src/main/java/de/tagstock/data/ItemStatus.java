package de.tagstock.data;

import androidx.annotation.StringRes;

import de.tagstock.R;

/** Zustand eines Artikels im Lager. */
public enum ItemStatus {
    VORHANDEN(R.string.status_vorhanden, R.color.status_vorhanden),
    VERLIEHEN(R.string.status_verliehen, R.color.status_verliehen),
    VERLOREN(R.string.status_verloren, R.color.status_verloren);

    @StringRes
    public final int labelRes;
    public final int colorRes;

    ItemStatus(@StringRes int labelRes, int colorRes) {
        this.labelRes = labelRes;
        this.colorRes = colorRes;
    }

    public static ItemStatus fromName(String name) {
        if (name != null) {
            for (ItemStatus status : values()) {
                if (status.name().equals(name)) {
                    return status;
                }
            }
        }
        return VORHANDEN;
    }
}
