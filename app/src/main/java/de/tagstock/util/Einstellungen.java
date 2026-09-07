package de.tagstock.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

/** App-Einstellungen: Design, Name fuer das Protokoll und Serverzugang. */
public final class Einstellungen {

    public static final String DESIGN_SYSTEM = "system";
    public static final String DESIGN_HELL = "hell";
    public static final String DESIGN_DUNKEL = "dunkel";

    private static final String DATEI = "tagstock";
    private static final String SCHLUESSEL_DESIGN = "design";
    private static final String SCHLUESSEL_NUTZER = "nutzer";
    private static final String SCHLUESSEL_SERVER = "serverUrl";
    private static final String SCHLUESSEL_TOKEN = "serverToken";
    private static final String SCHLUESSEL_TEAM_ID = "teamId";
    private static final String SCHLUESSEL_TEAM_NAME = "teamName";
    private static final String SCHLUESSEL_ROLLE = "teamRolle";
    private static final String SCHLUESSEL_SYNC = "letzterSync";

    private Einstellungen() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(DATEI, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ Design

    public static String design(Context context) {
        return prefs(context).getString(SCHLUESSEL_DESIGN, DESIGN_SYSTEM);
    }

    public static void setzeDesign(Context context, String design) {
        prefs(context).edit().putString(SCHLUESSEL_DESIGN, design).apply();
        anwenden(design);
    }

    /** Setzt den Nachtmodus passend zur Einstellung. */
    public static void anwenden(String design) {
        switch (design) {
            case DESIGN_HELL:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DESIGN_DUNKEL:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static void anwenden(Context context) {
        anwenden(design(context));
    }

    // ------------------------------------------------------------------ Nutzer

    /** Name, der im Aenderungsprotokoll steht. */
    public static String nutzer(Context context) {
        return prefs(context).getString(SCHLUESSEL_NUTZER, "");
    }

    public static void setzeNutzer(Context context, String name) {
        prefs(context).edit().putString(SCHLUESSEL_NUTZER, name).apply();
    }

    // ------------------------------------------------------------------ Server

    @Nullable
    public static String serverUrl(Context context) {
        String wert = prefs(context).getString(SCHLUESSEL_SERVER, null);
        return wert == null || wert.trim().isEmpty() ? null : wert.trim();
    }

    @Nullable
    public static String token(Context context) {
        return prefs(context).getString(SCHLUESSEL_TOKEN, null);
    }

    @Nullable
    public static String teamId(Context context) {
        return prefs(context).getString(SCHLUESSEL_TEAM_ID, null);
    }

    public static String teamName(Context context) {
        return prefs(context).getString(SCHLUESSEL_TEAM_NAME, "");
    }

    public static String rolle(Context context) {
        return prefs(context).getString(SCHLUESSEL_ROLLE, "mitglied");
    }

    public static long letzterSync(Context context) {
        return prefs(context).getLong(SCHLUESSEL_SYNC, 0L);
    }

    public static void setzeLetztenSync(Context context, long zeitpunkt) {
        prefs(context).edit().putLong(SCHLUESSEL_SYNC, zeitpunkt).apply();
    }

    public static void setzeServer(Context context, String url, String token) {
        prefs(context).edit()
                .putString(SCHLUESSEL_SERVER, url)
                .putString(SCHLUESSEL_TOKEN, token)
                .apply();
    }

    public static void setzeTeam(Context context, String id, String name, String rolle) {
        prefs(context).edit()
                .putString(SCHLUESSEL_TEAM_ID, id)
                .putString(SCHLUESSEL_TEAM_NAME, name)
                .putString(SCHLUESSEL_ROLLE, rolle)
                .apply();
    }

    /** true, wenn Serveradresse und Anmeldung hinterlegt sind. */
    public static boolean serverAktiv(Context context) {
        return serverUrl(context) != null && token(context) != null;
    }

    /** true, wenn der Nutzer im Team Aenderungen vornehmen darf. */
    public static boolean darfBearbeiten(Context context) {
        if (!serverAktiv(context)) {
            return true;
        }
        String rolle = rolle(context);
        return "admin".equals(rolle) || "lagerist".equals(rolle);
    }

    /** Nur das Team loesen; die Anmeldung am Server bleibt bestehen. */
    public static void teamVerlassen(Context context) {
        prefs(context).edit()
                .remove(SCHLUESSEL_TEAM_ID)
                .remove(SCHLUESSEL_TEAM_NAME)
                .remove(SCHLUESSEL_ROLLE)
                .remove(SCHLUESSEL_SYNC)
                .apply();
    }

    public static void abmelden(Context context) {
        prefs(context).edit()
                .remove(SCHLUESSEL_TOKEN)
                .remove(SCHLUESSEL_TEAM_ID)
                .remove(SCHLUESSEL_TEAM_NAME)
                .remove(SCHLUESSEL_ROLLE)
                .remove(SCHLUESSEL_SYNC)
                .apply();
    }
}
