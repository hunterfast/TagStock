package de.tagstock.server.daten;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Haelt das Schema beim Aktualisieren in Ordnung. Neue Tabellen legt schema.sql
 * an - neue Spalten in bestehenden Tabellen aber nicht, denn CREATE TABLE IF NOT
 * EXISTS laesst eine vorhandene Tabelle unberuehrt. Hier steht deshalb, welche
 * Spalten dazugekommen sind; fehlende werden beim Start ergaenzt.
 *
 * <p>So ueberlebt eine bestehende Datenbank jede Aktualisierung, ohne dass
 * jemand von Hand eingreifen muss. Spalten werden nur hinzugefuegt, nie
 * entfernt oder umgebaut.</p>
 */
@Component
public class Schemapflege implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(Schemapflege.class);

    /** Tabelle -> Spalte -> Deklaration, wie sie ein ALTER TABLE braucht. */
    private static final Map<String, Map<String, String>> ERWARTET = new LinkedHashMap<>();

    static {
        // Beispiel fuer kuenftige Erweiterungen - hier kommt jede neue Spalte hin:
        // spalten("artikel").put("gewicht", "REAL");
        spalten("artikel").put("bild_url", "TEXT");
        spalten("kategorien").put("geloescht", "INTEGER NOT NULL DEFAULT 0");
        spalten("anfragen").put("antwort", "TEXT");
    }

    private static Map<String, String> spalten(String tabelle) {
        return ERWARTET.computeIfAbsent(tabelle, name -> new LinkedHashMap<>());
    }

    private final JdbcTemplate jdbc;

    public Schemapflege(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments argumente) {
        for (Map.Entry<String, Map<String, String>> tabelle : ERWARTET.entrySet()) {
            if (!tabelleDa(tabelle.getKey())) {
                continue;
            }
            List<String> vorhanden = spaltenNamen(tabelle.getKey());
            for (Map.Entry<String, String> spalte : tabelle.getValue().entrySet()) {
                if (vorhanden.contains(spalte.getKey())) {
                    continue;
                }
                jdbc.execute("ALTER TABLE " + tabelle.getKey() + " ADD COLUMN "
                        + spalte.getKey() + " " + spalte.getValue());
                LOG.info("Spalte {}.{} ergaenzt", tabelle.getKey(), spalte.getKey());
            }
        }
    }

    private boolean tabelleDa(String name) {
        Integer anzahl = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                Integer.class, name);
        return anzahl != null && anzahl > 0;
    }

    private List<String> spaltenNamen(String tabelle) {
        List<String> namen = new ArrayList<>();
        jdbc.query("PRAGMA table_info(" + tabelle + ")", zeile -> {
            namen.add(zeile.getString("name"));
        });
        return namen;
    }
}
