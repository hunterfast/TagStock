package de.tagstock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import de.tagstock.data.AppDatabase;
import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.Protokoll;

/**
 * Prueft die Migration der Datenbank bis Version 3. Room prueft beim Oeffnen
 * zusaetzlich, ob das Schema exakt zu den Entities passt.
 */
@RunWith(RobolectricTestRunner.class)
public class MigrationTest {

    private static final String DB = "migration-test.db";

    private Context context;
    private AppDatabase db;

    @Before
    public void vorbereiten() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB);
    }

    @After
    public void aufraeumen() {
        if (db != null) {
            db.close();
            db = null;
        }
        context.deleteDatabase(DB);
    }

    /** Legt eine Datenbank im Format der angegebenen Version an. */
    private void alteDatenbank(int version, Fuellung fuellung) {
        SupportSQLiteOpenHelper.Configuration konfiguration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(DB)
                        .callback(new SupportSQLiteOpenHelper.Callback(version) {
                            @Override
                            public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                fuellung.fuellen(db);
                            }

                            @Override
                            public void onUpgrade(@NonNull SupportSQLiteDatabase db,
                                                  int alt, int neu) {
                            }
                        })
                        .build();
        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(konfiguration);
        helper.getWritableDatabase();
        helper.close();
    }

    private interface Fuellung {
        void fuellen(SupportSQLiteDatabase db);
    }

    private void migrieren() {
        db = Room.databaseBuilder(context, AppDatabase.class, DB)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                        AppDatabase.MIGRATION_3_4)
                .allowMainThreadQueries()
                .build();
        db.artikelDao().alle();
    }

    // ------------------------------------------------------------ Version 1

    private void schemaV1(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `lager` ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "`name` TEXT NOT NULL, `beschreibung` TEXT, `ort` TEXT, "
                + "`erstelltAm` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `items` ("
                + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "`lagerId` INTEGER NOT NULL, `name` TEXT NOT NULL, `beschreibung` TEXT, "
                + "`code` TEXT, `codeType` TEXT NOT NULL, `menge` INTEGER NOT NULL, "
                + "`status` TEXT NOT NULL, `verliehenAn` TEXT, `verliehenSeit` INTEGER, "
                + "`notiz` TEXT, `erstelltAm` INTEGER NOT NULL, `geaendertAm` INTEGER NOT NULL, "
                + "FOREIGN KEY(`lagerId`) REFERENCES `lager`(`id`)"
                + " ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_lagerId` ON `items` (`lagerId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_code` ON `items` (`code`)");
    }

    private void datenV1(SupportSQLiteDatabase db) {
        db.execSQL("INSERT INTO lager (id, name, beschreibung, ort, erstelltAm)"
                + " VALUES (1, 'Werkstatt', NULL, 'Regal A3', 1000)");
        db.execSQL("INSERT INTO items (id, lagerId, name, beschreibung, code, codeType, menge,"
                + " status, verliehenAn, verliehenSeit, notiz, erstelltAm, geaendertAm)"
                + " VALUES (1, 1, 'Akkuschrauber', 'Mit Ladegerät', '111', 'QR', 2,"
                + " 'VORHANDEN', NULL, NULL, 'Zweiter Akku fehlt', 1000, 1000)");
        db.execSQL("INSERT INTO items (id, lagerId, name, beschreibung, code, codeType, menge,"
                + " status, verliehenAn, verliehenSeit, notiz, erstelltAm, geaendertAm)"
                + " VALUES (2, 1, 'Bohrmaschine', NULL, '222', 'BARCODE', 1,"
                + " 'VERLIEHEN', 'Max', 5000, NULL, 1000, 1000)");
        db.execSQL("INSERT INTO items (id, lagerId, name, beschreibung, code, codeType, menge,"
                + " status, verliehenAn, verliehenSeit, notiz, erstelltAm, geaendertAm)"
                + " VALUES (3, 1, 'Zollstock', NULL, NULL, 'MANUELL', 1,"
                + " 'VERLOREN', NULL, NULL, NULL, 1000, 1000)");
    }

    @Test
    public void ausVersion1WirdDasNeueModell() {
        alteDatenbank(1, db -> {
            schemaV1(db);
            datenV1(db);
        });
        migrieren();

        List<Artikel> artikel = db.artikelDao().alle();
        assertEquals(3, artikel.size());

        Artikel akku = db.artikelDao().nachId(1);
        assertNotNull(akku);
        assertEquals("Akkuschrauber", akku.name);
        // Aus dem Lager wird der Standort, aus dem Lager-Ort der Lagerort.
        assertEquals("Werkstatt", akku.standort);
        assertEquals("Regal A3", akku.lagerort);
        assertEquals("111", akku.rfidUid);
        assertEquals(ArtikelStatus.VORHANDEN, akku.status);
        // Notiz haengt an der Beschreibung, damit nichts verloren geht.
        assertTrue(akku.beschreibung.contains("Ladegerät"));
        assertTrue(akku.beschreibung.contains("Zweiter Akku"));

        Artikel bohrer = db.artikelDao().nachId(2);
        assertEquals(ArtikelStatus.VERLIEHEN, bohrer.status);
        assertEquals("Max", bohrer.verliehenAn);

        Artikel zollstock = db.artikelDao().nachId(3);
        assertEquals(ArtikelStatus.NICHT_VORHANDEN, zollstock.status);
        assertNull(zollstock.rfidUid);
    }

    @Test
    public void ausleihenLandenImProtokoll() {
        alteDatenbank(1, db -> {
            schemaV1(db);
            datenV1(db);
        });
        migrieren();

        List<Protokoll> protokoll = db.protokollDao().alle();
        boolean gefunden = false;
        for (Protokoll eintrag : protokoll) {
            if (Protokoll.VERLIEHEN.equals(eintrag.aktion) && "Max".equals(eintrag.neuerWert)) {
                gefunden = true;
            }
        }
        assertTrue("Ausleihe fehlt im Protokoll", gefunden);
    }

    @Test
    public void standardkategorienStehenBereit() {
        alteDatenbank(1, db -> {
            schemaV1(db);
            datenV1(db);
        });
        migrieren();
        assertTrue(db.kategorieDao().anzahl() >= 8);
    }

    // ------------------------------------------------------------ Version 2

    @Test
    public void ausVersion2WirdDasNeueModell() {
        alteDatenbank(2, db -> {
            db.execSQL("CREATE TABLE IF NOT EXISTS `lager` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT NOT NULL, `beschreibung` TEXT, `ort` TEXT, "
                    + "`erstelltAm` INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `items` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`lagerId` INTEGER NOT NULL, `name` TEXT NOT NULL, `beschreibung` TEXT, "
                    + "`menge` INTEGER NOT NULL, `mengeVerloren` INTEGER NOT NULL, "
                    + "`fotoPfad` TEXT, `notiz` TEXT, `erstelltAm` INTEGER NOT NULL, "
                    + "`geaendertAm` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`lagerId`) REFERENCES `lager`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_lagerId` ON `items` (`lagerId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `codes` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`itemId` INTEGER NOT NULL, `wert` TEXT NOT NULL, `typ` TEXT NOT NULL, "
                    + "`erfasstAm` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_codes_wert` ON `codes` (`wert`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_codes_itemId` ON `codes` (`itemId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `verleih` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`itemId` INTEGER NOT NULL, `person` TEXT NOT NULL, "
                    + "`menge` INTEGER NOT NULL, `ausgeliehenAm` INTEGER NOT NULL, "
                    + "`zurueckAm` INTEGER, `notiz` TEXT, "
                    + "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_verleih_itemId` ON `verleih` (`itemId`)");

            db.execSQL("INSERT INTO lager (id, name, beschreibung, ort, erstelltAm)"
                    + " VALUES (5, 'Keller', NULL, 'Schrank links', 1000)");
            db.execSQL("INSERT INTO items (id, lagerId, name, beschreibung, menge, mengeVerloren,"
                    + " fotoPfad, notiz, erstelltAm, geaendertAm)"
                    + " VALUES (7, 5, 'Leiter', NULL, 1, 0, NULL, NULL, 1000, 2000)");
            db.execSQL("INSERT INTO codes (itemId, wert, typ, erfasstAm)"
                    + " VALUES (7, 'ABC123', 'NFC', 1500)");
            db.execSQL("INSERT INTO verleih (itemId, person, menge, ausgeliehenAm, zurueckAm, notiz)"
                    + " VALUES (7, 'Anna', 1, 3000, NULL, NULL)");
        });
        migrieren();

        Artikel leiter = db.artikelDao().nachId(7);
        assertNotNull(leiter);
        assertEquals("Keller", leiter.standort);
        assertEquals("Schrank links", leiter.lagerort);
        assertEquals("ABC123", leiter.rfidUid);
        assertEquals(ArtikelStatus.VERLIEHEN, leiter.status);
        assertEquals("Anna", leiter.verliehenAn);
        assertEquals(2000L, leiter.geaendertAm);
    }

    // ------------------------------------------------------------ Version 3

    /**
     * Aus Version 3 kommen nur drei Spalten dazu - der Bestand muss dabei
     * unangetastet bleiben, und die neuen Felder stehen auf "kein Behaelter".
     */
    @Test
    public void ausVersion3KommenDieBehaelterDazu() {
        alteDatenbank(3, db -> {
            db.execSQL("CREATE TABLE IF NOT EXISTS `artikel` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`serverId` TEXT, `teamId` TEXT, `rfidUid` TEXT, "
                    + "`name` TEXT NOT NULL, `beschreibung` TEXT, `kategorie` TEXT, "
                    + "`standort` TEXT, `lagerort` TEXT, `fotoPfad` TEXT, `bildUrl` TEXT, "
                    + "`status` TEXT NOT NULL, `verliehenAn` TEXT, `rueckgabeDatum` INTEGER, "
                    + "`zuletztGescannt` INTEGER, `scanWarnung` TEXT NOT NULL, "
                    + "`erstelltAm` INTEGER NOT NULL, `geaendertAm` INTEGER NOT NULL, "
                    + "`offen` INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artikel_rfidUid`"
                    + " ON `artikel` (`rfidUid`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_artikel_serverId`"
                    + " ON `artikel` (`serverId`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `kategorien` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`serverId` TEXT, "
                    + "`teamId` TEXT, "
                    + "`name` TEXT NOT NULL, "
                    + "`reihenfolge` INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_kategorien_name`"
                    + " ON `kategorien` (`name`)");
            db.execSQL("CREATE TABLE IF NOT EXISTS `protokoll` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`serverId` TEXT, "
                    + "`artikelId` INTEGER NOT NULL, "
                    + "`artikelName` TEXT NOT NULL, "
                    + "`aktion` TEXT NOT NULL, "
                    + "`alterWert` TEXT, "
                    + "`neuerWert` TEXT, "
                    + "`nutzer` TEXT, "
                    + "`zeitpunkt` INTEGER NOT NULL, "
                    + "`offen` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`artikelId`) REFERENCES `artikel`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_protokoll_artikelId`"
                    + " ON `protokoll` (`artikelId`)");
            db.execSQL("INSERT INTO artikel (id, rfidUid, name, standort, status, scanWarnung,"
                    + " erstelltAm, geaendertAm, offen)"
                    + " VALUES (3, 'BOX-1', 'Ikea-Box blau', 'Werkstatt', 'vorhanden', '1j',"
                    + " 1000, 2000, 0)");
        });
        migrieren();

        Artikel box = db.artikelDao().nachId(3);
        assertNotNull(box);
        assertEquals("Ikea-Box blau", box.name);
        assertEquals("Werkstatt", box.standort);
        assertFalse(box.istBehaelter);
        assertNull(box.behaelterArt);
        assertNull(box.behaelterKennung);

        // Und ab jetzt laesst sich daraus ein Behaelter machen.
        box.istBehaelter = true;
        box.behaelterArt = "Box";
        db.artikelDao().update(box);
        Artikel zange = new Artikel();
        zange.name = "Zange";
        zange.rfidUid = "WZ-1";
        zange.behaelterKennung = "BOX-1";
        db.artikelDao().insert(zange);

        assertEquals(1, db.artikelDao().inhaltVon("BOX-1").size());
        assertEquals("Zange", db.artikelDao().inhaltVon("BOX-1").get(0).name);
        assertEquals(1, db.artikelDao().behaelter().size());
    }
}
