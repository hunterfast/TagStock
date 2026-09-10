package de.tagstock.server.daten;

import de.tagstock.server.modell.Produktinfo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Zwischenspeicher fuer nachgeschlagene Produktdaten. */
@Repository
public class GtinDaten {

    private final JdbcTemplate jdbc;

    public GtinDaten(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Produktinfo> MAPPER = (zeile, nummer) -> {
        Produktinfo info = new Produktinfo();
        info.gtin = zeile.getString("gtin");
        info.gefunden = zeile.getInt("gefunden") == 1;
        info.name = zeile.getString("name");
        info.marke = zeile.getString("marke");
        info.kategorie = zeile.getString("kategorie");
        info.quelle = zeile.getString("quelle");
        info.geholtAm = zeile.getLong("geholt_am");
        return info;
    };

    public Produktinfo nachGtin(String gtin) {
        List<Produktinfo> treffer = jdbc.query("SELECT * FROM gtin_cache WHERE gtin = ?",
                MAPPER, gtin);
        return treffer.isEmpty() ? null : treffer.get(0);
    }

    public void merken(Produktinfo info) {
        jdbc.update("INSERT INTO gtin_cache (gtin, gefunden, name, marke, kategorie, quelle,"
                        + " geholt_am) VALUES (?,?,?,?,?,?,?)"
                        + " ON CONFLICT(gtin) DO UPDATE SET gefunden = excluded.gefunden,"
                        + " name = excluded.name, marke = excluded.marke,"
                        + " kategorie = excluded.kategorie, quelle = excluded.quelle,"
                        + " geholt_am = excluded.geholt_am",
                info.gtin, info.gefunden ? 1 : 0, info.name, info.marke, info.kategorie,
                info.quelle, info.geholtAm);
    }
}
