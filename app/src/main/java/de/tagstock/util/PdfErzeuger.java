package de.tagstock.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import de.tagstock.R;
import de.tagstock.data.Artikel;

/** Erzeugt Etikettenbogen und Leihbelege als PDF - ohne Zusatzbibliothek. */
public final class PdfErzeuger {

    // DIN A4 in Punkt (72 dpi): 595 x 842
    private static final int SEITE_BREITE = 595;
    private static final int SEITE_HOEHE = 842;
    private static final int RAND = 34;
    private static final int SPALTEN = 4;
    private static final int ZEILEN = 6;

    private PdfErzeuger() {
    }

    /** Etikettenbogen mit QR-Code und Name, 24 Stueck je Seite. */
    public static void etiketten(Context context, List<Artikel> artikel, OutputStream ziel)
            throws IOException {
        PdfDocument dokument = new PdfDocument();

        int zellenBreite = (SEITE_BREITE - 2 * RAND) / SPALTEN;
        int zellenHoehe = (SEITE_HOEHE - 2 * RAND) / ZEILEN;
        int proSeite = SPALTEN * ZEILEN;

        Paint rahmen = new Paint();
        rahmen.setStyle(Paint.Style.STROKE);
        rahmen.setColor(Color.LTGRAY);
        rahmen.setStrokeWidth(0.7f);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.BLACK);
        text.setTextSize(8f);
        text.setTextAlign(Paint.Align.CENTER);

        Paint klein = new Paint(text);
        klein.setTextSize(6f);
        klein.setColor(Color.GRAY);

        int seiten = Math.max(1, (int) Math.ceil(artikel.size() / (double) proSeite));
        for (int seite = 0; seite < seiten; seite++) {
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                    SEITE_BREITE, SEITE_HOEHE, seite + 1).create();
            PdfDocument.Page page = dokument.startPage(info);
            Canvas canvas = page.getCanvas();

            for (int i = 0; i < proSeite; i++) {
                int index = seite * proSeite + i;
                if (index >= artikel.size()) {
                    break;
                }
                Artikel eintrag = artikel.get(index);
                int spalte = i % SPALTEN;
                int zeile = i / SPALTEN;
                int links = RAND + spalte * zellenBreite;
                int oben = RAND + zeile * zellenHoehe;

                canvas.drawRect(links + 2, oben + 2, links + zellenBreite - 2,
                        oben + zellenHoehe - 2, rahmen);

                Bitmap qr = QrErzeuger.erzeuge(eintrag.qrWert(), 300);
                int qrKante = Math.min(zellenBreite, zellenHoehe) - 34;
                if (qr != null) {
                    int qrLinks = links + (zellenBreite - qrKante) / 2;
                    canvas.drawBitmap(qr, null,
                            new Rect(qrLinks, oben + 8, qrLinks + qrKante, oben + 8 + qrKante),
                            null);
                    qr.recycle();
                }

                int mitte = links + zellenBreite / 2;
                canvas.drawText(kuerzen(eintrag.name, 22), mitte, oben + qrKante + 20, text);
                if (eintrag.standort != null && !eintrag.standort.isEmpty()) {
                    canvas.drawText(kuerzen(eintrag.standort, 24), mitte, oben + qrKante + 30, klein);
                }
            }
            dokument.finishPage(page);
        }

        schreiben(dokument, ziel);
    }

    /** Einfacher Leihbeleg zum Ausdrucken oder Weitergeben. */
    public static void leihbeleg(Context context, Artikel artikel, OutputStream ziel)
            throws IOException {
        PdfDocument dokument = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                SEITE_BREITE, SEITE_HOEHE, 1).create();
        PdfDocument.Page page = dokument.startPage(info);
        Canvas canvas = page.getCanvas();

        Paint titel = new Paint(Paint.ANTI_ALIAS_FLAG);
        titel.setColor(Color.BLACK);
        titel.setTextSize(22f);
        titel.setFakeBoldText(true);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.BLACK);
        text.setTextSize(12f);

        Paint grau = new Paint(text);
        grau.setColor(Color.GRAY);
        grau.setTextSize(10f);

        Paint linie = new Paint();
        linie.setColor(Color.LTGRAY);
        linie.setStrokeWidth(0.8f);

        int y = 70;
        canvas.drawText(context.getString(R.string.leihbeleg_titel), RAND, y, titel);
        y += 18;
        canvas.drawText(context.getString(R.string.leihbeleg_erstellt,
                Formatter.datumZeit(context, System.currentTimeMillis())), RAND, y, grau);

        y += 24;
        canvas.drawLine(RAND, y, SEITE_BREITE - RAND, y, linie);
        y += 30;

        y = zeile(canvas, text, grau, y, context.getString(R.string.artikel_name), artikel.name);
        y = zeile(canvas, text, grau, y, context.getString(R.string.artikel_kategorie),
                wert(artikel.kategorie));
        y = zeile(canvas, text, grau, y, context.getString(R.string.artikel_standort),
                wert(artikel.standort));
        y = zeile(canvas, text, grau, y, context.getString(R.string.artikel_lagerort),
                wert(artikel.lagerort));
        y = zeile(canvas, text, grau, y, context.getString(R.string.artikel_kennung),
                wert(artikel.rfidUid));
        y = zeile(canvas, text, grau, y, context.getString(R.string.artikel_verliehen_an),
                wert(artikel.verliehenAn));
        y = zeile(canvas, text, grau, y, context.getString(R.string.artikel_rueckgabe),
                artikel.rueckgabeDatum == null ? "—"
                        : Formatter.datum(context, artikel.rueckgabeDatum));

        Bitmap qr = QrErzeuger.erzeuge(artikel.qrWert(), 300);
        if (qr != null) {
            canvas.drawBitmap(qr, null,
                    new Rect(SEITE_BREITE - RAND - 110, 60, SEITE_BREITE - RAND, 170), null);
            qr.recycle();
        }

        y += 60;
        canvas.drawLine(RAND, y, RAND + 200, y, linie);
        canvas.drawLine(SEITE_BREITE - RAND - 200, y, SEITE_BREITE - RAND, y, linie);
        y += 14;
        canvas.drawText(context.getString(R.string.leihbeleg_uebergeber), RAND, y, grau);
        canvas.drawText(context.getString(R.string.leihbeleg_empfaenger),
                SEITE_BREITE - RAND - 200, y, grau);

        dokument.finishPage(page);
        schreiben(dokument, ziel);
    }

    private static int zeile(Canvas canvas, Paint text, Paint grau, int y,
                             String bezeichnung, String wert) {
        canvas.drawText(bezeichnung, RAND, y, grau);
        canvas.drawText(wert, RAND + 140, y, text);
        return y + 26;
    }

    private static String wert(String wert) {
        return wert == null || wert.trim().isEmpty() ? "—" : wert;
    }

    private static String kuerzen(String wert, int zeichen) {
        if (wert == null) {
            return "";
        }
        return wert.length() <= zeichen ? wert : wert.substring(0, zeichen - 1) + "…";
    }

    private static void schreiben(PdfDocument dokument, OutputStream ziel) throws IOException {
        try {
            dokument.writeTo(ziel);
        } finally {
            dokument.close();
        }
    }
}
