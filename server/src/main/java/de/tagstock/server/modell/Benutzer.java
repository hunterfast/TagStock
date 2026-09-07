package de.tagstock.server.modell;

/** Ein Konto auf dem Server. Das Passwort wird nie nach aussen gegeben. */
public class Benutzer {

    public String id;
    public String email;
    public String name;
    public long erstelltAm;

    /** BCrypt-Hash; wird von der API ausgeblendet. */
    public transient String passwort;

    public Benutzer() {
    }

    public Benutzer(String id, String email, String name, long erstelltAm) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.erstelltAm = erstelltAm;
    }
}
