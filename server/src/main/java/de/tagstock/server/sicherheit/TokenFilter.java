package de.tagstock.server.sicherheit;

import de.tagstock.server.daten.BenutzerDaten;
import de.tagstock.server.modell.Benutzer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Liest den Bearer-Token aus dem Kopf der Anfrage und haengt das zugehoerige
 * Konto an. Wer keinen gueltigen Token hat, kommt trotzdem durch - die
 * einzelnen Endpunkte entscheiden, was sie ohne Anmeldung erlauben.
 */
@Component
public class TokenFilter extends OncePerRequestFilter {

    private final BenutzerDaten benutzerDaten;
    private final Tokens tokens;

    public TokenFilter(BenutzerDaten benutzerDaten, Tokens tokens) {
        this.benutzerDaten = benutzerDaten;
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest anfrage, HttpServletResponse antwort,
                                    FilterChain kette) throws ServletException, IOException {
        String kopf = anfrage.getHeader("Authorization");
        if (kopf != null && kopf.startsWith("Bearer ")) {
            String token = kopf.substring(7).trim();
            Benutzer benutzer = benutzerDaten.zuToken(tokens.hash(token),
                    System.currentTimeMillis());
            if (benutzer != null) {
                anfrage.setAttribute(Angemeldet.ATTRIBUT, benutzer);
            }
        }
        kette.doFilter(anfrage, antwort);
    }
}
