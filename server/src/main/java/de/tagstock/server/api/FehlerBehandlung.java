package de.tagstock.server.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Gibt Fehler als schlichtes JSON zurueck. */
@RestControllerAdvice
public class FehlerBehandlung {

    @ExceptionHandler(ApiFehler.class)
    public ResponseEntity<Map<String, Object>> apiFehler(ApiFehler fehler) {
        return ResponseEntity.status(fehler.status)
                .body(Map.of("fehler", fehler.getMessage(), "status", fehler.status.value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unerwartet(Exception fehler) {
        String meldung = fehler.getMessage() == null ? "Unerwarteter Fehler" : fehler.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("fehler", meldung, "status", 500));
    }
}
