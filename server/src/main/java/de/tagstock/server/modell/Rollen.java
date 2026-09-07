package de.tagstock.server.modell;

/** Rollen innerhalb eines Teams. */
public final class Rollen {

    /** Darf alles, auch Mitglieder verwalten. */
    public static final String ADMIN = "admin";
    /** Darf den Bestand pflegen und Anfragen entscheiden. */
    public static final String LAGERIST = "lagerist";
    /** Darf lesen und Anfragen stellen. */
    public static final String MITGLIED = "mitglied";

    private Rollen() {
    }

    public static boolean gueltig(String rolle) {
        return ADMIN.equals(rolle) || LAGERIST.equals(rolle) || MITGLIED.equals(rolle);
    }

    public static boolean darfBearbeiten(String rolle) {
        return ADMIN.equals(rolle) || LAGERIST.equals(rolle);
    }

    public static boolean istAdmin(String rolle) {
        return ADMIN.equals(rolle);
    }
}
