package de.tagstock.util;

import android.app.Activity;
import android.content.Context;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import de.tagstock.data.CodeType;

/** Kapselt das Lesen von NFC-Tags im Reader-Mode. */
public final class NfcHelper {

    private static final int READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_A
                    | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_NFC_F
                    | NfcAdapter.FLAG_READER_NFC_V
                    | NfcAdapter.FLAG_READER_NFC_BARCODE;

    private NfcHelper() {
    }

    @Nullable
    public static NfcAdapter getAdapter(Context context) {
        return NfcAdapter.getDefaultAdapter(context);
    }

    /** true, wenn das Geraet NFC besitzt und es eingeschaltet ist. */
    public static boolean isReady(Context context) {
        NfcAdapter adapter = getAdapter(context);
        return adapter != null && adapter.isEnabled();
    }

    public static boolean hasHardware(Context context) {
        return getAdapter(context) != null;
    }

    /**
     * Aktiviert den Reader-Mode. Der Callback wird auf einem Hintergrund-Thread
     * aufgerufen, nicht auf dem Main-Thread.
     */
    public static void enableReader(Activity activity, NfcAdapter.ReaderCallback callback) {
        NfcAdapter adapter = getAdapter(activity);
        if (adapter == null) {
            return;
        }
        Bundle options = new Bundle();
        // Etwas Zeit lassen, damit auch langsamere Tags sicher erkannt werden.
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
        adapter.enableReaderMode(activity, callback, READER_FLAGS, options);
    }

    public static void disableReader(Activity activity) {
        NfcAdapter adapter = getAdapter(activity);
        if (adapter != null) {
            adapter.disableReaderMode(activity);
        }
    }

    /** Liest UID und - falls vorhanden - den Textinhalt des Tags. */
    public static ScanResult read(Tag tag) {
        String uid = toHex(tag.getId());
        String text = readNdefText(tag);
        return new ScanResult(uid, CodeType.NFC, text);
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /** Gibt den ersten Text- oder URI-Datensatz des Tags zurueck, sonst null. */
    @Nullable
    public static String readNdefText(Tag tag) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) {
            return null;
        }
        NdefMessage message = ndef.getCachedNdefMessage();
        if (message == null) {
            try {
                ndef.connect();
                message = ndef.getNdefMessage();
            } catch (IOException | android.nfc.FormatException e) {
                return null;
            } finally {
                try {
                    ndef.close();
                } catch (IOException ignored) {
                    // Tag wurde bereits entfernt - nicht relevant.
                }
            }
        }
        if (message == null) {
            return null;
        }
        for (NdefRecord record : message.getRecords()) {
            String value = decodeRecord(record);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private static String decodeRecord(NdefRecord record) {
        if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN
                && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)) {
            byte[] payload = record.getPayload();
            if (payload.length == 0) {
                return null;
            }
            int status = payload[0] & 0xFF;
            int languageLength = status & 0x3F;
            Charset charset = (status & 0x80) == 0 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16;
            int offset = 1 + languageLength;
            if (offset > payload.length) {
                return null;
            }
            return new String(payload, offset, payload.length - offset, charset);
        }
        android.net.Uri uri = record.toUri();
        return uri != null ? uri.toString() : null;
    }
}
