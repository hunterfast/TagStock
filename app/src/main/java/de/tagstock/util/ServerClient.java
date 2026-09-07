package de.tagstock.util;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
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

    private final String basis;
    @Nullable
    private final String token;

    public ServerClient(String serverUrl, @Nullable String token) {
        String bereinigt = serverUrl.trim();
        while (bereinigt.endsWith("/")) {
            bereinigt = bereinigt.substring(0, bereinigt.length() - 1);
        }
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

    private String hole(String verfahren, String pfad, @Nullable JSONObject koerper)
            throws IOException {
        HttpURLConnection verbindung = (HttpURLConnection) new URL(basis + pfad).openConnection();
        try {
            verbindung.setRequestMethod(verfahren);
            verbindung.setConnectTimeout(VERBINDUNG_MS);
            verbindung.setReadTimeout(LESEN_MS);
            verbindung.setRequestProperty("Accept", "application/json; charset=utf-8");
            if (token != null && !token.isEmpty()) {
                verbindung.setRequestProperty("Authorization", "Bearer " + token);
            }
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
