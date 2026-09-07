package de.tagstock.server.sicherheit;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

/** Passwoerter werden ausschliesslich als BCrypt-Hash gespeichert. */
@Component
public class Passwoerter {

    public String hashen(String klartext) {
        return BCrypt.hashpw(klartext, BCrypt.gensalt(10));
    }

    public boolean passt(String klartext, String hash) {
        if (klartext == null || hash == null || hash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(klartext, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
