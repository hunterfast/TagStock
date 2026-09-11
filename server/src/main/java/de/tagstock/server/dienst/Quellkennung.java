package de.tagstock.server.dienst;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Merkt sich, in welchem Zustand eine Adresse zuletzt war, und sagt, ob sich
 * seither etwas geaendert hat.
 *
 * <p>Dafuer genuegt die Kennung, die GitHub jeder Antwort mitgibt (ETag).
 * Schickt man sie beim naechsten Mal als {@code If-None-Match} zurueck und ist
 * alles beim Alten, antwortet GitHub mit 304 und ohne Inhalt - und zaehlt die
 * Anfrage nicht gegen das Stundenkonto. Deshalb darf man damit im Minutentakt
 * nachfragen, statt stundenlang zu warten.</p>
 */
public class Quellkennung {

    private final HttpClient client;
    private final String annahme;
    private String kennung;

    public Quellkennung(String annahme) {
        this.annahme = annahme;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Die zuletzt gesehene Kennung - leer, solange noch nie gefragt wurde. */
    public String kennung() {
        return kennung;
    }

    /**
     * Fragt nach und sagt, ob sich etwas geaendert hat. Beim allerersten Mal
     * wird die Kennung nur gemerkt: Was da ist, hat der Server beim Start
     * ohnehin schon angesehen, und ein Fehlalarm bei jedem Neustart waere
     * unnoetige Arbeit.
     */
    public boolean veraendert(String adresse, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder bau = HttpRequest.newBuilder(URI.create(adresse))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "TagStock-Server")
                .header("Accept", annahme)
                .GET();
        if (token != null && !token.isEmpty()) {
            bau.header("Authorization", "Bearer " + token);
        }
        boolean erstesMal = kennung == null;
        if (!erstesMal) {
            bau.header("If-None-Match", kennung);
        }
        HttpResponse<Void> antwort = client.send(bau.build(),
                HttpResponse.BodyHandlers.discarding());
        if (antwort.statusCode() == 304) {
            return false;
        }
        if (antwort.statusCode() >= 400) {
            throw new IOException("Antwort " + antwort.statusCode() + " von " + adresse);
        }
        String neue = antwort.headers().firstValue("etag").orElse(null);
        if (neue == null) {
            // Ohne Kennung laesst sich nichts vergleichen; dann lieber nichts
            // melden, als bei jeder Runde einen Alarm auszuloesen.
            return false;
        }
        boolean anders = !neue.equals(kennung);
        kennung = neue;
        return anders && !erstesMal;
    }
}
