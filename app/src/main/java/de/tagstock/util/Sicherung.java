package de.tagstock.util;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.Bestand;
import de.tagstock.data.Kategorie;
import de.tagstock.data.Protokoll;
import de.tagstock.data.ScanWarnung;

/**
 * Sicherung und Wiederherstellung des Bestands. Das JSON-Format entspricht den
 * Feldern der Weboberflaeche, CSV ist die flache Liste fuer die Tabelle.
 */
public final class Sicherung {

    public static final int FORMAT_VERSION = 3;
    private static final String TRENNER = ";";

    private Sicherung() {
    }

    // -------------------------------------------------------------------- JSON

    public static String alsJson(Bestand bestand) throws JSONException {
        JSONObject wurzel = new JSONObject();
        wurzel.put("app", "TagStock");
        wurzel.put("version", FORMAT_VERSION);
        wurzel.put("erstelltAm", System.currentTimeMillis());

        JSONArray artikelArray = new JSONArray();
        for (Artikel artikel : bestand.artikel) {
            artikelArray.put(alsJson(artikel));
        }
        wurzel.put("artikel", artikelArray);

        JSONArray kategorieArray = new JSONArray();
        for (Kategorie kategorie : bestand.kategorien) {
            JSONObject o = new JSONObject();
            o.put("name", kategorie.name);
            o.put("reihenfolge", kategorie.reihenfolge);
            kategorieArray.put(o);
        }
        wurzel.put("kategorien", kategorieArray);

        JSONArray protokollArray = new JSONArray();
        for (Protokoll eintrag : bestand.protokoll) {
            JSONObject o = new JSONObject();
            o.put("artikel_id", eintrag.artikelId);
            o.put("artikel_name", eintrag.artikelName);
            o.put("aktion", eintrag.aktion);
            o.put("alter_wert", text(eintrag.alterWert));
            o.put("neuer_wert", text(eintrag.neuerWert));
            o.put("nutzer", text(eintrag.nutzer));
            o.put("zeitpunkt", eintrag.zeitpunkt);
            protokollArray.put(o);
        }
        wurzel.put("protokoll", protokollArray);

        return wurzel.toString(2);
    }

    /** Ein Artikel im Format der Weboberflaeche. */
    public static JSONObject alsJson(Artikel artikel) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", artikel.id);
        o.put("server_id", text(artikel.serverId));
        o.put("rfid_uid", text(artikel.rfidUid));
        o.put("name", artikel.name);
        o.put("beschreibung", text(artikel.beschreibung));
        o.put("kategorie", text(artikel.kategorie));
        o.put("standort", text(artikel.standort));
        o.put("lagerort", text(artikel.lagerort));
        o.put("status", artikel.status.schluessel);
        o.put("verliehen_an", text(artikel.verliehenAn));
        o.put("rueckgabe_datum", artikel.rueckgabeDatum == null
                ? JSONObject.NULL : artikel.rueckgabeDatum);
        o.put("zuletzt_gescannt", artikel.zuletztGescannt == null
                ? JSONObject.NULL : artikel.zuletztGescannt);
        o.put("scan_warnung", artikel.scanWarnung.schluessel);
        o.put("erstellt_am", artikel.erstelltAm);
        o.put("geaendert_am", artikel.geaendertAm);
        return o;
    }

    public static Bestand ausJson(String inhalt) throws JSONException {
        JSONObject wurzel = new JSONObject(inhalt);
        Bestand bestand = new Bestand();

        JSONArray artikelArray = wurzel.optJSONArray("artikel");
        if (artikelArray != null) {
            for (int i = 0; i < artikelArray.length(); i++) {
                bestand.artikel.add(artikelAus(artikelArray.getJSONObject(i)));
            }
        }

        JSONArray kategorieArray = wurzel.optJSONArray("kategorien");
        if (kategorieArray != null) {
            for (int i = 0; i < kategorieArray.length(); i++) {
                JSONObject o = kategorieArray.getJSONObject(i);
                String name = o.optString("name", "");
                if (!name.isEmpty()) {
                    bestand.kategorien.add(new Kategorie(name, o.optInt("reihenfolge", i)));
                }
            }
        }

        JSONArray protokollArray = wurzel.optJSONArray("protokoll");
        if (protokollArray != null) {
            for (int i = 0; i < protokollArray.length(); i++) {
                JSONObject o = protokollArray.getJSONObject(i);
                Protokoll eintrag = new Protokoll();
                eintrag.artikelId = o.optLong("artikel_id");
                eintrag.artikelName = o.optString("artikel_name", "");
                eintrag.aktion = o.optString("aktion", "");
                eintrag.alterWert = textOderNull(o, "alter_wert");
                eintrag.neuerWert = textOderNull(o, "neuer_wert");
                eintrag.nutzer = textOderNull(o, "nutzer");
                eintrag.zeitpunkt = o.optLong("zeitpunkt", System.currentTimeMillis());
                bestand.protokoll.add(eintrag);
            }
        }
        return bestand;
    }

    /** Liest einen Artikel aus JSON - auch aus einer Server-Antwort. */
    public static Artikel artikelAus(JSONObject o) {
        Artikel artikel = new Artikel();
        artikel.id = o.optLong("id");
        artikel.serverId = textOderNull(o, "server_id");
        artikel.rfidUid = textOderNull(o, "rfid_uid");
        artikel.name = o.optString("name", "");
        artikel.beschreibung = textOderNull(o, "beschreibung");
        artikel.kategorie = textOderNull(o, "kategorie");
        artikel.standort = textOderNull(o, "standort");
        artikel.lagerort = textOderNull(o, "lagerort");
        artikel.status = ArtikelStatus.vonSchluessel(o.optString("status"));
        artikel.verliehenAn = textOderNull(o, "verliehen_an");
        artikel.rueckgabeDatum = o.isNull("rueckgabe_datum") ? null : o.optLong("rueckgabe_datum");
        artikel.zuletztGescannt = o.isNull("zuletzt_gescannt") ? null : o.optLong("zuletzt_gescannt");
        artikel.scanWarnung = ScanWarnung.vonSchluessel(o.optString("scan_warnung"));
        artikel.erstelltAm = o.optLong("erstellt_am", System.currentTimeMillis());
        artikel.geaendertAm = o.optLong("geaendert_am", artikel.erstelltAm);
        return artikel;
    }

    private static Object text(String wert) {
        return wert == null ? JSONObject.NULL : wert;
    }

    private static String textOderNull(JSONObject o, String schluessel) {
        if (o.isNull(schluessel)) {
            return null;
        }
        String wert = o.optString(schluessel, "");
        return wert.isEmpty() ? null : wert;
    }

    // --------------------------------------------------------------------- CSV

    public static String alsCsv(Context context, Bestand bestand) {
        SimpleDateFormat datum = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append(zeile("Name", "Beschreibung", "Kategorie", "Standort", "Lagerort", "Status",
                "Verliehen an", "Rückgabe", "Kennung", "Zuletzt gescannt", "Geändert am"));

        for (Artikel artikel : bestand.artikel) {
            csv.append(zeile(
                    artikel.name,
                    feldText(artikel.beschreibung),
                    feldText(artikel.kategorie),
                    feldText(artikel.standort),
                    feldText(artikel.lagerort),
                    context.getString(artikel.status.labelRes),
                    feldText(artikel.verliehenAn),
                    artikel.rueckgabeDatum == null ? "" : datum.format(new Date(artikel.rueckgabeDatum)),
                    feldText(artikel.rfidUid),
                    artikel.zuletztGescannt == null ? "" : datum.format(new Date(artikel.zuletztGescannt)),
                    datum.format(new Date(artikel.geaendertAm))));
        }
        return csv.toString();
    }

    private static String feldText(String wert) {
        return wert == null ? "" : wert;
    }

    private static String zeile(String... felder) {
        StringBuilder zeile = new StringBuilder();
        for (int i = 0; i < felder.length; i++) {
            if (i > 0) {
                zeile.append(TRENNER);
            }
            zeile.append(feld(felder[i]));
        }
        return zeile.append('\n').toString();
    }

    private static String feld(String wert) {
        if (wert == null) {
            return "";
        }
        if (wert.contains(TRENNER) || wert.contains("\"") || wert.contains("\n")
                || wert.contains("\r")) {
            return '"' + wert.replace("\"", "\"\"") + '"';
        }
        return wert;
    }

    // ------------------------------------------------------------------ Dateien

    public static void schreibe(Context context, Uri ziel, String inhalt) throws IOException {
        try (OutputStream out = context.getContentResolver().openOutputStream(ziel, "wt")) {
            if (out == null) {
                throw new IOException("Datei nicht beschreibbar");
            }
            out.write(inhalt.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static String lies(Context context, Uri quelle) throws IOException {
        StringBuilder inhalt = new StringBuilder();
        try (InputStream in = context.getContentResolver().openInputStream(quelle)) {
            if (in == null) {
                throw new IOException("Datei nicht lesbar");
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String zeile;
            while ((zeile = reader.readLine()) != null) {
                inhalt.append(zeile).append('\n');
            }
        }
        return inhalt.toString();
    }

    public static String dateiname(String endung) {
        String datum = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new Date());
        return "tagstock-" + datum + "." + endung;
    }
}
