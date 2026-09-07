package de.tagstock.util;

import androidx.annotation.StringRes;

import de.tagstock.R;

/** Herkunft eines gescannten Codes. */
public enum CodeArt {
    BARCODE(R.string.codeart_barcode),
    QR(R.string.codeart_qr),
    NFC(R.string.codeart_nfc),
    MANUELL(R.string.codeart_manuell);

    @StringRes
    public final int labelRes;

    CodeArt(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    public static CodeArt vonName(String name) {
        if (name != null) {
            for (CodeArt art : values()) {
                if (art.name().equals(name)) {
                    return art;
                }
            }
        }
        return MANUELL;
    }
}
