package de.tagstock.data;

import androidx.annotation.StringRes;

import de.tagstock.R;

/** Herkunft des gespeicherten Codes eines Artikels. */
public enum CodeType {
    KEINER(R.string.codetype_keiner),
    BARCODE(R.string.codetype_barcode),
    QR(R.string.codetype_qr),
    NFC(R.string.codetype_nfc),
    MANUELL(R.string.codetype_manuell);

    @StringRes
    public final int labelRes;

    CodeType(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    public static CodeType fromName(String name) {
        if (name != null) {
            for (CodeType type : values()) {
                if (type.name().equals(name)) {
                    return type;
                }
            }
        }
        return KEINER;
    }
}
