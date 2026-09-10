package de.tagstock.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tagstock.server.daten.Schemapflege;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Eine Datenbank aus einer aelteren Fassung darf beim Aktualisieren nicht
 * stehenbleiben: fehlende Spalten kommen beim Start dazu.
 */
class SchemapflegeTest {

    @Test
    void fehlendeSpaltenKommenBeimStartDazu() {
        File datei = new File("build/tmp/schemapflege-test.db");
        //noinspection ResultOfMethodCallIgnored
        datei.getParentFile().mkdirs();
        //noinspection ResultOfMethodCallIgnored
        datei.delete();

        DriverManagerDataSource quelle = new DriverManagerDataSource(
                "jdbc:sqlite:" + datei.getPath());
        quelle.setDriverClassName("org.sqlite.JDBC");
        JdbcTemplate jdbc = new JdbcTemplate(quelle);

        // So sah die Tabelle vor der Fassung mit Bildern aus.
        jdbc.execute("CREATE TABLE artikel (id TEXT PRIMARY KEY, team_id TEXT NOT NULL,"
                + " name TEXT NOT NULL, geaendert_am INTEGER NOT NULL)");

        new Schemapflege(jdbc).run(null);

        assertTrue(spalten(jdbc).contains("bild_url"),
                "bild_url fehlt: " + spalten(jdbc));

        // Ein zweiter Start darf nicht stolpern.
        new Schemapflege(jdbc).run(null);
        assertTrue(spalten(jdbc).contains("bild_url"));
    }

    private List<String> spalten(JdbcTemplate jdbc) {
        List<String> namen = new ArrayList<>();
        for (java.util.Map<String, Object> zeile
                : jdbc.queryForList("PRAGMA table_info(artikel)")) {
            namen.add(String.valueOf(zeile.get("name")));
        }
        return namen;
    }
}
