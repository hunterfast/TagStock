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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.tagstock.data.Bestand;
import de.tagstock.data.Code;
import de.tagstock.data.CodeType;
import de.tagstock.data.Item;
import de.tagstock.data.Lager;
import de.tagstock.data.Verleih;

/**
 * Sicherung und Wiederherstellung des Bestands. JSON enthaelt alles und laesst
 * sich zurueckspielen, CSV ist die flache Liste fuer die Tabellenkalkulation.
 * Fotos bleiben aussen vor, sie liegen nur auf dem Geraet.
 */
public final class Sicherung {

    public static final int FORMAT_VERSION = 2;
    private static final String TRENNER = ";";

    private Sicherung() {
    }

    // -------------------------------------------------------------------- JSON

    public static String alsJson(Bestand bestand) throws JSONException {
        JSONObject wurzel = new JSONObject();
        wurzel.put("app", "TagStock");
        wurzel.put("version", FORMAT_VERSION);
        wurzel.put("erstelltAm", System.currentTimeMillis());

        JSONArray lagerArray = new JSONArray();
        for (Lager lager : bestand.lager) {
            JSONObject o = new JSONObject();
            o.put("id", lager.id);
            o.put("name", lager.name);
            o.put("beschreibung", lager.beschreibung == null ? JSONObject.NULL : lager.beschreibung);
            o.put("ort", lager.ort == null ? JSONObject.NULL : lager.ort);
            o.put("erstelltAm", lager.erstelltAm);
            lagerArray.put(o);
        }
        wurzel.put("lager", lagerArray);

        JSONArray itemArray = new JSONArray();
        for (Item item : bestand.items) {
            JSONObject o = new JSONObject();
            o.put("id", item.id);
            o.put("lagerId", item.lagerId);
            o.put("name", item.name);
            o.put("beschreibung", item.beschreibung == null ? JSONObject.NULL : item.beschreibung);
            o.put("menge", item.menge);
            o.put("mengeVerloren", item.mengeVerloren);
            o.put("notiz", item.notiz == null ? JSONObject.NULL : item.notiz);
            o.put("erstelltAm", item.erstelltAm);
            o.put("geaendertAm", item.geaendertAm);
            itemArray.put(o);
        }
        wurzel.put("items", itemArray);

        JSONArray codeArray = new JSONArray();
        for (Code code : bestand.codes) {
            JSONObject o = new JSONObject();
            o.put("itemId", code.itemId);
            o.put("wert", code.wert);
            o.put("typ", code.typ.name());
            o.put("erfasstAm", code.erfasstAm);
            codeArray.put(o);
        }
        wurzel.put("codes", codeArray);

        JSONArray verleihArray = new JSONArray();
        for (Verleih verleih : bestand.verleihe) {
            JSONObject o = new JSONObject();
            o.put("itemId", verleih.itemId);
            o.put("person", verleih.person);
            o.put("menge", verleih.menge);
            o.put("ausgeliehenAm", verleih.ausgeliehenAm);
            o.put("zurueckAm", verleih.zurueckAm == null ? JSONObject.NULL : verleih.zurueckAm);
            o.put("notiz", verleih.notiz == null ? JSONObject.NULL : verleih.notiz);
            verleihArray.put(o);
        }
        wurzel.put("verleih", verleihArray);

        return wurzel.toString(2);
    }

    public static Bestand ausJson(String inhalt) throws JSONException {
        JSONObject wurzel = new JSONObject(inhalt);
        Bestand bestand = new Bestand();

        JSONArray lagerArray = wurzel.optJSONArray("lager");
        if (lagerArray != null) {
            for (int i = 0; i < lagerArray.length(); i++) {
                JSONObject o = lagerArray.getJSONObject(i);
                Lager lager = new Lager();
                lager.id = o.optLong("id");
                lager.importId = lager.id;
                lager.name = o.optString("name", "");
                lager.beschreibung = textOderNull(o, "beschreibung");
                lager.ort = textOderNull(o, "ort");
                lager.erstelltAm = o.optLong("erstelltAm", System.currentTimeMillis());
                bestand.lager.add(lager);
            }
        }

        JSONArray itemArray = wurzel.optJSONArray("items");
        if (itemArray != null) {
            for (int i = 0; i < itemArray.length(); i++) {
                JSONObject o = itemArray.getJSONObject(i);
                Item item = new Item();
                item.id = o.optLong("id");
                item.lagerId = o.optLong("lagerId");
                item.name = o.optString("name", "");
                item.beschreibung = textOderNull(o, "beschreibung");
                item.menge = o.optInt("menge", 1);
                item.mengeVerloren = o.optInt("mengeVerloren", 0);
                item.notiz = textOderNull(o, "notiz");
                item.erstelltAm = o.optLong("erstelltAm", System.currentTimeMillis());
                item.geaendertAm = o.optLong("geaendertAm", item.erstelltAm);
                bestand.items.add(item);
            }
        }

        JSONArray codeArray = wurzel.optJSONArray("codes");
        if (codeArray != null) {
            for (int i = 0; i < codeArray.length(); i++) {
                JSONObject o = codeArray.getJSONObject(i);
                String wert = o.optString("wert", "");
                if (wert.isEmpty()) {
                    continue;
                }
                Code code = new Code(o.optLong("itemId"), wert,
                        CodeType.fromName(o.optString("typ")));
                code.erfasstAm = o.optLong("erfasstAm", System.currentTimeMillis());
                bestand.codes.add(code);
            }
        }

        JSONArray verleihArray = wurzel.optJSONArray("verleih");
        if (verleihArray != null) {
            for (int i = 0; i < verleihArray.length(); i++) {
                JSONObject o = verleihArray.getJSONObject(i);
                Verleih verleih = new Verleih();
                verleih.itemId = o.optLong("itemId");
                verleih.person = o.optString("person", "");
                verleih.menge = o.optInt("menge", 1);
                verleih.ausgeliehenAm = o.optLong("ausgeliehenAm", System.currentTimeMillis());
                verleih.zurueckAm = o.isNull("zurueckAm") ? null : o.optLong("zurueckAm");
                verleih.notiz = textOderNull(o, "notiz");
                bestand.verleihe.add(verleih);
            }
        }
        return bestand;
    }

    private static String textOderNull(JSONObject o, String schluessel) {
        return o.isNull(schluessel) ? null : o.optString(schluessel, null);
    }

    // --------------------------------------------------------------------- CSV

    /** Flache Artikelliste mit Stueckzahlen je Zustand. */
    public static String alsCsv(Bestand bestand) {
        Map<Long, String> lagerNamen = new HashMap<>();
        for (Lager lager : bestand.lager) {
            lagerNamen.put(lager.id, lager.name);
        }

        Map<Long, Integer> verliehen = new HashMap<>();
        Map<Long, StringBuilder> personen = new HashMap<>();
        for (Verleih verleih : bestand.verleihe) {
            if (verleih.zurueckAm != null) {
                continue;
            }
            Integer bisher = verliehen.get(verleih.itemId);
            verliehen.put(verleih.itemId, (bisher == null ? 0 : bisher) + verleih.menge);
            StringBuilder namen = personen.get(verleih.itemId);
            if (namen == null) {
                namen = new StringBuilder();
                personen.put(verleih.itemId, namen);
            }
            if (namen.length() > 0) {
                namen.append(", ");
            }
            namen.append(verleih.person);
        }

        Map<Long, StringBuilder> codes = new HashMap<>();
        for (Code code : bestand.codes) {
            StringBuilder werte = codes.get(code.itemId);
            if (werte == null) {
                werte = new StringBuilder();
                codes.put(code.itemId, werte);
            }
            if (werte.length() > 0) {
                werte.append(" | ");
            }
            werte.append(code.wert);
        }

        SimpleDateFormat datum = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
        StringBuilder csv = new StringBuilder();
        // Kopfzeile mit BOM, damit Excel die Umlaute richtig liest.
        csv.append('\uFEFF');
        csv.append(zeile("Lager", "Artikel", "Beschreibung", "Gesamt", "Vorhanden", "Verliehen",
                "Verloren", "Verliehen an", "Codes", "Notiz", "Geaendert am"));

        for (Item item : bestand.items) {
            int istVerliehen = verliehen.containsKey(item.id) ? verliehen.get(item.id) : 0;
            int vorhanden = Math.max(0, item.menge - istVerliehen - item.mengeVerloren);
            csv.append(zeile(
                    text(lagerNamen.get(item.lagerId)),
                    item.name,
                    text(item.beschreibung),
                    String.valueOf(item.menge),
                    String.valueOf(vorhanden),
                    String.valueOf(istVerliehen),
                    String.valueOf(item.mengeVerloren),
                    personen.containsKey(item.id) ? personen.get(item.id).toString() : "",
                    codes.containsKey(item.id) ? codes.get(item.id).toString() : "",
                    text(item.notiz),
                    datum.format(new Date(item.geaendertAm))));
        }
        return csv.toString();
    }

    private static String text(String wert) {
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

    /** Dateiname mit Datum, z. B. tagstock-2026-08-30.json. */
    public static String dateiname(String endung) {
        String datum = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
                .format(new Date());
        return "tagstock-" + datum + "." + endung;
    }
}
