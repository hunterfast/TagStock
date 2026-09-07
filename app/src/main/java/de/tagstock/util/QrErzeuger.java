package de.tagstock.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/** Erzeugt QR-Codes auf dem Geraet - ohne Internetdienst. */
public final class QrErzeuger {

    private QrErzeuger() {
    }

    /** Schwarz-weisser QR-Code mit der uebergebenen Kantenlaenge in Pixeln. */
    @Nullable
    public static Bitmap erzeuge(String inhalt, int kante) {
        if (inhalt == null || inhalt.isEmpty()) {
            return null;
        }
        Map<EncodeHintType, Object> hinweise = new EnumMap<>(EncodeHintType.class);
        hinweise.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hinweise.put(EncodeHintType.MARGIN, 1);
        hinweise.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    inhalt, BarcodeFormat.QR_CODE, kante, kante, hinweise);
            int breite = matrix.getWidth();
            int hoehe = matrix.getHeight();
            int[] pixel = new int[breite * hoehe];
            for (int y = 0; y < hoehe; y++) {
                int zeile = y * breite;
                for (int x = 0; x < breite; x++) {
                    pixel[zeile + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixel, 0, breite, 0, 0, breite, hoehe);
            return bitmap;
        } catch (WriterException | IllegalArgumentException e) {
            return null;
        }
    }
}
