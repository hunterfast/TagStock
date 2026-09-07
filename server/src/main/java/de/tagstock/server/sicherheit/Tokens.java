package de.tagstock.server.sicherheit;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Zugangstoken. Ausgegeben wird ein Zufallswert; gespeichert wird nur dessen
 * Hash, damit ein Blick in die Datenbank keine gueltigen Token liefert.
 */
@Component
public class Tokens {

    private final SecureRandom zufall = new SecureRandom();

    public String neu() {
        byte[] werte = new byte[32];
        zufall.nextBytes(werte);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(werte);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt", e);
        }
    }
}
