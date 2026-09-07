package de.tagstock.server.api;

import org.springframework.http.HttpStatus;

/** Fehler mit vorgegebenem HTTP-Status und verstaendlicher Meldung. */
public class ApiFehler extends RuntimeException {

    public final HttpStatus status;

    public ApiFehler(HttpStatus status, String meldung) {
        super(meldung);
        this.status = status;
    }

    public static ApiFehler anmeldungNoetig() {
        return new ApiFehler(HttpStatus.UNAUTHORIZED, "Anmeldung erforderlich");
    }

    public static ApiFehler verboten(String meldung) {
        return new ApiFehler(HttpStatus.FORBIDDEN, meldung);
    }

    public static ApiFehler nichtGefunden(String meldung) {
        return new ApiFehler(HttpStatus.NOT_FOUND, meldung);
    }

    public static ApiFehler ungueltig(String meldung) {
        return new ApiFehler(HttpStatus.BAD_REQUEST, meldung);
    }

    public static ApiFehler konflikt(String meldung) {
        return new ApiFehler(HttpStatus.CONFLICT, meldung);
    }
}
