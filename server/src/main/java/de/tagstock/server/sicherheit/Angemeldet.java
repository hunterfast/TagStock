package de.tagstock.server.sicherheit;

import de.tagstock.server.modell.Benutzer;

/** Haelt das angemeldete Konto waehrend einer Anfrage fest. */
public final class Angemeldet {

    public static final String ATTRIBUT = "tagstock.benutzer";

    private Angemeldet() {
    }

    public static Benutzer aus(jakarta.servlet.http.HttpServletRequest anfrage) {
        Object wert = anfrage.getAttribute(ATTRIBUT);
        return wert instanceof Benutzer ? (Benutzer) wert : null;
    }
}
