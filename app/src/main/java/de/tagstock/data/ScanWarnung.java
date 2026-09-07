package de.tagstock.data;

import androidx.annotation.StringRes;

import java.util.Calendar;

import de.tagstock.R;

/** Zeitraum, nach dem ein nicht mehr gescannter Artikel als vermisst gilt. */
public enum ScanWarnung {

    HALBJAHR("6m", 6, R.string.scanwarnung_6m),
    JAHR("1j", 12, R.string.scanwarnung_1j),
    ZWEI_JAHRE("2j", 24, R.string.scanwarnung_2j),
    NIEMALS("niemals", 0, R.string.scanwarnung_niemals);

    public final String schluessel;
    public final int monate;

    @StringRes
    public final int labelRes;

    ScanWarnung(String schluessel, int monate, @StringRes int labelRes) {
        this.schluessel = schluessel;
        this.monate = monate;
        this.labelRes = labelRes;
    }

    public static ScanWarnung vonSchluessel(String schluessel) {
        if (schluessel != null) {
            for (ScanWarnung warnung : values()) {
                if (warnung.schluessel.equals(schluessel) || warnung.name().equals(schluessel)) {
                    return warnung;
                }
            }
        }
        return JAHR;
    }

    /** Zeitpunkt, vor dem ein Scan als zu alt gilt. */
    public long grenze() {
        Calendar kalender = Calendar.getInstance();
        kalender.add(Calendar.MONTH, -monate);
        return kalender.getTimeInMillis();
    }
}
