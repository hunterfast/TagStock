package de.tagstock.util;

import android.content.Intent;

import de.tagstock.data.CodeType;

/** Ergebnis eines Scans (Barcode, QR-Code oder NFC-Tag). */
public class ScanResult {

    public static final String EXTRA_CODE = "de.tagstock.extra.CODE";
    public static final String EXTRA_CODE_TYPE = "de.tagstock.extra.CODE_TYPE";
    public static final String EXTRA_LABEL = "de.tagstock.extra.LABEL";

    public final String code;
    public final CodeType codeType;
    /** Zusatzinfo, z. B. "EAN-13" oder der Textinhalt eines NFC-Tags. */
    public final String label;

    public ScanResult(String code, CodeType codeType, String label) {
        this.code = code;
        this.codeType = codeType;
        this.label = label;
    }

    public Intent toIntent() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_CODE, code);
        intent.putExtra(EXTRA_CODE_TYPE, codeType.name());
        intent.putExtra(EXTRA_LABEL, label);
        return intent;
    }

    public static ScanResult fromIntent(Intent intent) {
        if (intent == null || intent.getStringExtra(EXTRA_CODE) == null) {
            return null;
        }
        return new ScanResult(
                intent.getStringExtra(EXTRA_CODE),
                CodeType.fromName(intent.getStringExtra(EXTRA_CODE_TYPE)),
                intent.getStringExtra(EXTRA_LABEL));
    }
}
