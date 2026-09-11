package de.tagstock.server.dienst;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Behaelt das Projekt im Auge. Bisher sah der Server nur alle sechs Stunden
 * nach einer neuen App - wer etwas veroeffentlicht hatte, wartete also unter
 * Umstaenden den halben Tag oder musste den Knopf in der Oberflaeche druecken.
 *
 * <p>Jetzt fragt der Server jede Minute nach, ob sich an der Liste der
 * Veroeffentlichungen etwas geaendert hat. Das kostet fast nichts: Ist alles
 * beim Alten, antwortet GitHub mit 304 und ohne Inhalt, und solche Antworten
 * zaehlen nicht gegen das Anfragekonto (siehe {@link Quellkennung}). Erst wenn
 * sich wirklich etwas getan hat, wird richtig nachgesehen - dann aber
 * sofort.</p>
 */
@Component
public class Quellwache implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(Quellwache.class);
    private static final String ALS_JSON = "application/vnd.github+json";

    private final Appwache appwache;
    private final Selbstpflege pflege;
    private final Quellkennung kennung = new Quellkennung(ALS_JSON);
    private final String quelle;
    private final String token;
    private final boolean erlaubt;
    private final long taktMs;

    private volatile long zuletztGesehen;
    private volatile boolean laeuft;

    public Quellwache(Appwache appwache, Selbstpflege pflege,
                      @Value("${tagstock.quelle-beobachten:true}") boolean erlaubt,
                      @Value("${tagstock.quelle-takt-sekunden:60}") long taktSekunden,
                      @Value("${tagstock.app-quelle:}") String quelle,
                      @Value("${tagstock.github-token:}") String token) {
        this.appwache = appwache;
        this.pflege = pflege;
        this.erlaubt = erlaubt;
        // Unter einer halben Minute waere es Zappeln, nicht Beobachten.
        this.taktMs = Math.max(30L, taktSekunden) * 1000L;
        this.quelle = quelle == null || quelle.isBlank()
                ? "https://api.github.com/repos/hunterfast/TagStock/releases?per_page=30"
                : quelle.trim();
        this.token = token == null ? "" : token.trim();
    }

    /** Laeuft die Beobachtung? Steht so auch in der Oberflaeche. */
    public boolean laeuft() {
        return laeuft;
    }

    /** Wann zuletzt etwas gefunden wurde - 0, solange nichts war. */
    public long zuletztGesehen() {
        return zuletztGesehen;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent ereignis) {
        if (!erlaubt) {
            LOG.info("Quelle wird nicht beobachtet (ausgeschaltet)");
            return;
        }
        if (token.isEmpty()) {
            LOG.info("Quelle wird nicht beobachtet - kein Zugangsschluessel");
            return;
        }
        laeuft = true;
        Thread laeufer = new Thread(this::schleife, "quell-wache");
        laeufer.setDaemon(true);
        laeufer.start();
    }

    private void schleife() {
        while (true) {
            try {
                Thread.sleep(taktMs);
            } catch (InterruptedException unterbrochen) {
                Thread.currentThread().interrupt();
                laeuft = false;
                return;
            }
            try {
                runde();
            } catch (InterruptedException unterbrochen) {
                Thread.currentThread().interrupt();
                laeuft = false;
                return;
            } catch (Exception fehler) {
                // Ein Aussetzer im Netz ist kein Grund aufzuhoeren.
                LOG.debug("Nachfragen misslungen: {}", fehler.toString());
            }
        }
    }

    /**
     * Eine Runde: nachfragen und, wenn sich etwas getan hat, sofort
     * nachsehen lassen. Gibt zurueck, ob es etwas Neues gab.
     */
    public boolean runde() throws Exception {
        if (!kennung.veraendert(quelle, token)) {
            return false;
        }
        LOG.info("Am Projekt hat sich etwas getan - es wird sofort nachgesehen");
        zuletztGesehen = System.currentTimeMillis();
        appwache.nachsehen(true);
        pflege.neuestesMerken();
        return true;
    }
}
