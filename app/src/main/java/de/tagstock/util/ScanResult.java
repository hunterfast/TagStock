package de.tagstock.util;

import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

import de.tagstock.data.CodeType;

/** Ergebnis eines Scans (Barcode, QR-Code oder NFC-Tag). */
public class ScanResult {

    public static final String EXTRA_CODE = "de.tagstock.extra.CODE";
    public static final String EXTRA_CODE_TYPE = "de.tagstock.extra.CODE_TYPE";
    public static final String EXTRA_LABEL = "de.tagstock.extra.LABEL";

    public final String code;
    public final CodeType codeType;
    /** Zusatzinfo: Barcode-Format oder der Textinhalt eines NFC-Tags. */
    public final String label;

    public ScanResult(String code, CodeType codeType, String label) {
        this.code = code;
        this.codeType = codeType;
        this.label = label;
    }

    /**
     * Werte, unter denen ein Artikel gesucht werden kann. Bei NFC sind das die
     * UID und - falls beschrieben - der Tag-Inhalt, damit auch ein Tag gefunden
     * wird, dessen UID sich nicht auslesen laesst.
     */
    public List<String> werte() {
        List<String> werte = new ArrayList<>(2);
        if (code != null && !code.isEmpty()) {
            werte.add(code);
        }
        if (codeType == CodeType.NFC && label != null && !label.isEmpty()
                && !label.equals(code)) {
            werte.add(label);
        }
        return werte;
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
