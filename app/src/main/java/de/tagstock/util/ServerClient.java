package de.tagstock.util;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Zugriff auf den TagStock-Server. Bewusst schlicht gehalten: HttpURLConnection
 * und JSON, keine zusaetzliche Bibliothek. Alle Aufrufe blockieren und gehoeren
 * deshalb in einen Hintergrund-Thread.
 */
public class ServerClient {

    /** Fehler mit der Meldung, die der Server geschickt hat. */
    public static class ServerFehler extends IOException {
        public final int status;

        public ServerFehler(int status, String meldung) {
            super(meldung);
            this.status = status;
        }

        /** true, wenn die Anmeldung abgelaufen ist. */
        public boolean istAbgemeldet() {
            return status == 401;
        }
    }

    private static final int VERBINDUNG_MS = 8000;
    private static final int LESEN_MS = 20000;

    private final String wurzel;
    private final String basis;
    @Nullable
    private final String token;

    public ServerClient(String serverUrl, @Nullable String token) {
        String bereinigt = serverUrl.trim();
        while (bereinigt.endsWith("/")) {
            bereinigt = bereinigt.substring(0, bereinigt.length() - 1);
        }
        this.wurzel = bereinigt;
        this.basis = bereinigt + "/api/v1";
        this.token = token;
    }

    // ------------------------------------------------------------------ Konten

    public JSONObject status() throws IOException, JSONException {
        return objekt(hole("GET", "/status", null));
    }

    public JSONObject registrieren(String email, String name, String passwort,
                                   @Nullable String code) throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("email", email);
        koerper.put("name", name);
        koerper.put("passwort", passwort);
        if (code != null && !code.isEmpty()) {
            koerper.put("code", code);
        }
        return objekt(hole("POST", "/auth/registrieren", koerper));
    }

    public JSONObject anmelden(String email, String passwort) throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("email", email);
        koerper.put("passwort", passwort);
        return objekt(hole("POST", "/auth/anmelden", koerper));
    }

    public void abmelden() throws IOException, JSONException {
        hole("POST", "/auth/abmelden", new JSONObject());
    }

    public JSONObject ich() throws IOException, JSONException {
        return objekt(hole("GET", "/ich", null));
    }

    // ------------------------------------------------------------------- Teams

    public JSONArray teams() throws IOException, JSONException {
        return feld(hole("GET", "/teams", null));
    }

    public JSONObject teamAnlegen(String name) throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("name", name);
        return objekt(hole("POST", "/teams", koerper));
    }

    public JSONObject beitreten(String code) throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("code", code);
        return objekt(hole("POST", "/teams/beitreten", koerper));
    }

    public JSONObject einladung(String teamId, String rolle) throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("rolle", rolle);
        return objekt(hole("POST", "/teams/" + teamId + "/einladung", koerper));
    }

    public JSONArray mitglieder(String teamId) throws IOException, JSONException {
        return feld(hole("GET", "/teams/" + teamId + "/mitglieder", null));
    }

    public void rolleSetzen(String teamId, String benutzerId, String rolle)
            throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("rolle", rolle);
        hole("PUT", "/teams/" + teamId + "/mitglieder/" + benutzerId, koerper);
    }

    /** Entfernt ein Konto aus dem Team - oder einen selbst, wenn es das eigene ist. */
    public void mitgliedEntfernen(String teamId, String benutzerId) throws IOException {
        hole("DELETE", "/teams/" + teamId + "/mitglieder/" + benutzerId, null);
    }

    // ---------------------------------------------------------------- Abgleich

    public JSONObject abgleichHolen(String teamId, long seit) throws IOException, JSONException {
        return objekt(hole("GET", "/teams/" + teamId + "/sync?seit=" + seit, null));
    }

    public JSONObject abgleichSenden(String teamId, long seit, JSONArray artikel,
                                     JSONArray protokoll) throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("artikel", artikel);
        koerper.put("protokoll", protokoll);
        return objekt(hole("POST", "/teams/" + teamId + "/sync?seit=" + seit, koerper));
    }

    // -------------------------------------------------------------- Kategorien

    public JSONArray kategorien(String teamId) throws IOException, JSONException {
        return feld(hole("GET", "/teams/" + teamId + "/kategorien", null));
    }

    public JSONObject kategorieAnlegen(String teamId, String name, int reihenfolge)
            throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("name", name);
        koerper.put("reihenfolge", reihenfolge);
        return objekt(hole("POST", "/teams/" + teamId + "/kategorien", koerper));
    }

    public JSONObject kategorieAendern(String teamId, String kategorieId, String name,
                                       int reihenfolge) throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("name", name);
        koerper.put("reihenfolge", reihenfolge);
        return objekt(hole("PUT", "/teams/" + teamId + "/kategorien/" + kategorieId, koerper));
    }

    public void kategorieLoeschen(String teamId, String kategorieId) throws IOException {
        hole("DELETE", "/teams/" + teamId + "/kategorien/" + kategorieId, null);
    }

    // ------------------------------------------------------------------ Bilder

    /**
     * Legt das Bild eines Artikels auf dem Server ab und gibt dessen Adresse
     * zurueck. Ein vorheriges Bild ersetzt der Server dabei.
     */
    public JSONObject bildHochladen(String teamId, String artikelServerId, byte[] daten,
                                    String typ) throws IOException, JSONException {
        HttpURLConnection verbindung = verbinden(new URL(
                basis + "/teams/" + teamId + "/artikel/" + artikelServerId + "/bild"), "POST");
        try {
            verbindung.setDoOutput(true);
            verbindung.setFixedLengthStreamingMode(daten.length);
            verbindung.setRequestProperty("Content-Type", typ);
            try (OutputStream aus = verbindung.getOutputStream()) {
                aus.write(daten);
            }
            int status = verbindung.getResponseCode();
            String antwort = lies(status >= 400
                    ? verbindung.getErrorStream() : verbindung.getInputStream());
            if (status >= 400) {
                throw new ServerFehler(status, meldung(antwort, status));
            }
            return objekt(antwort);
        } finally {
            verbindung.disconnect();
        }
    }

    /** Holt ein Bild. Die Adresse kommt vom Server und beginnt mit /api/v1. */
    public byte[] bildHolen(String bildUrl) throws IOException {
        HttpURLConnection verbindung = verbinden(new URL(wurzel + bildUrl), "GET");
        try {
            int status = verbindung.getResponseCode();
            if (status >= 400) {
                throw new ServerFehler(status, meldung(lies(verbindung.getErrorStream()), status));
            }
            try (InputStream ein = verbindung.getInputStream();
                 ByteArrayOutputStream aus = new ByteArrayOutputStream()) {
                byte[] puffer = new byte[8192];
                int gelesen;
                while ((gelesen = ein.read(puffer)) != -1) {
                    aus.write(puffer, 0, gelesen);
                }
                return aus.toByteArray();
            }
        } finally {
            verbindung.disconnect();
        }
    }

    // ---------------------------------------------------------------- Anfragen

    public JSONArray anfragen(String teamId) throws IOException, JSONException {
        return feld(hole("GET", "/teams/" + teamId + "/anfragen", null));
    }

    public JSONObject anfrageStellen(String teamId, String artikelId, String nachricht,
                                     @Nullable Long bis) throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("artikelId", artikelId);
        koerper.put("nachricht", nachricht);
        if (bis != null) {
            koerper.put("datumBis", bis);
        }
        return objekt(hole("POST", "/teams/" + teamId + "/anfragen", koerper));
    }

    public JSONObject anfrageEntscheiden(String teamId, String anfrageId, boolean genehmigt,
                                         String antwort) throws IOException, JSONException {
        JSONObject koerper = new JSONObject();
        koerper.put("genehmigt", genehmigt);
        koerper.put("antwort", antwort);
        return objekt(hole("POST", "/teams/" + teamId + "/anfragen/" + anfrageId + "/entscheiden",
                koerper));
    }

    // ------------------------------------------------------------------ Technik

    private HttpURLConnection verbinden(URL adresse, String verfahren) throws IOException {
        HttpURLConnection verbindung = (HttpURLConnection) adresse.openConnection();
        verbindung.setRequestMethod(verfahren);
        verbindung.setConnectTimeout(VERBINDUNG_MS);
        verbindung.setReadTimeout(LESEN_MS);
        if (token != null && !token.isEmpty()) {
            verbindung.setRequestProperty("Authorization", "Bearer " + token);
        }
        return verbindung;
    }

    private String hole(String verfahren, String pfad, @Nullable JSONObject koerper)
            throws IOException {
        HttpURLConnection verbindung = verbinden(new URL(basis + pfad), verfahren);
        try {
            verbindung.setRequestProperty("Accept", "application/json; charset=utf-8");
            if (koerper != null) {
                verbindung.setDoOutput(true);
                verbindung.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] daten = koerper.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream aus = verbindung.getOutputStream()) {
                    aus.write(daten);
                }
            }

            int status = verbindung.getResponseCode();
            String antwort = lies(status >= 400
                    ? verbindung.getErrorStream() : verbindung.getInputStream());
            if (status >= 400) {
                throw new ServerFehler(status, meldung(antwort, status));
            }
            return antwort;
        } finally {
            verbindung.disconnect();
        }
    }

    private String lies(@Nullable InputStream strom) throws IOException {
        if (strom == null) {
            return "";
        }
        StringBuilder inhalt = new StringBuilder();
        try (BufferedReader leser = new BufferedReader(
                new InputStreamReader(strom, StandardCharsets.UTF_8))) {
            String zeile;
            while ((zeile = leser.readLine()) != null) {
                inhalt.append(zeile);
            }
        }
        return inhalt.toString();
    }

    /** Holt die Fehlermeldung des Servers heraus, sonst eine allgemeine. */
    private String meldung(String antwort, int status) {
        try {
            JSONObject objekt = new JSONObject(antwort);
            String fehler = objekt.optString("fehler", "");
            if (!fehler.isEmpty()) {
                return fehler;
            }
        } catch (JSONException ignored) {
            // Keine JSON-Antwort - dann bleibt der Status.
        }
        return "Server meldet Fehler " + status;
    }

    private JSONObject objekt(String antwort) throws JSONException {
        return antwort.isEmpty() ? new JSONObject() : new JSONObject(antwort);
    }

    private JSONArray feld(String antwort) throws JSONException {
        return antwort.isEmpty() ? new JSONArray() : new JSONArray(antwort);
    }
}
