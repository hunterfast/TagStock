package de.tagstock;

import static org.junit.Assert.assertEquals;
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
import de.tagstock.data.Code;
import de.tagstock.data.CodeType;
import de.tagstock.data.ItemWithState;
import de.tagstock.data.Verleih;

/**
 * Prueft die Migration von Version 1 auf 2: Codes und Ausleihen ziehen in
 * eigene Tabellen um, der Status wird zu Stueckzahlen. Room selbst prueft beim
 * Oeffnen zusaetzlich, ob das Schema exakt zu den Entities passt.
 */
@RunWith(RobolectricTestRunner.class)
public class MigrationTest {

    private static final String DB = "migration-test.db";

    private Context context;
    private AppDatabase db;

    @Before
    public void datenbankV1Anlegen() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB);

        SupportSQLiteOpenHelper.Configuration konfiguration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(DB)
                        .callback(new SupportSQLiteOpenHelper.Callback(1) {
                            @Override
                            public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                schemaV1(db);
                                datenV1(db);
                            }

                            @Override
                            public void onUpgrade(@NonNull SupportSQLiteDatabase db,
                                                  int alt, int neu) {
                                // In Version 1 gab es noch nichts zu migrieren.
                            }
                        })
                        .build();

        SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory()
                .create(konfiguration);
        helper.getWritableDatabase();
        helper.close();
    }

    @After
    public void aufraeumen() {
        if (db != null) {
            db.close();
        }
        context.deleteDatabase(DB);
    }

    /** Das Schema, wie Room es in Version 1 angelegt hat. */
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
                + " VALUES (1, 'Werkstatt', NULL, 'Keller', 1000)");
        // Vorhanden, mit Code
        db.execSQL("INSERT INTO items (id, lagerId, name, beschreibung, code, codeType, menge,"
                + " status, verliehenAn, verliehenSeit, notiz, erstelltAm, geaendertAm)"
                + " VALUES (1, 1, 'Akkuschrauber', NULL, '111', 'QR', 2,"
                + " 'VORHANDEN', NULL, NULL, NULL, 1000, 1000)");
        // Verliehen, mit Ausleiher
        db.execSQL("INSERT INTO items (id, lagerId, name, beschreibung, code, codeType, menge,"
                + " status, verliehenAn, verliehenSeit, notiz, erstelltAm, geaendertAm)"
                + " VALUES (2, 1, 'Bohrmaschine', NULL, '222', 'BARCODE', 1,"
                + " 'VERLIEHEN', 'Max', 5000, NULL, 1000, 1000)");
        // Verloren, und mit einem bereits vergebenen Code
        db.execSQL("INSERT INTO items (id, lagerId, name, beschreibung, code, codeType, menge,"
                + " status, verliehenAn, verliehenSeit, notiz, erstelltAm, geaendertAm)"
                + " VALUES (3, 1, 'Zollstock', NULL, '111', 'QR', 1,"
                + " 'VERLOREN', NULL, NULL, NULL, 1000, 1000)");
    }

    private void migrieren() {
        db = Room.databaseBuilder(context, AppDatabase.class, DB)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build();
        // Erster Zugriff loest die Migration aus.
        db.lagerDao().getAll();
    }

    @Test
    public void lagerUndArtikelBleibenErhalten() {
        migrieren();
        assertEquals(1, db.lagerDao().getAll().size());
        assertEquals(3, db.itemDao().getAll().size());
        assertEquals("Werkstatt", db.lagerDao().getById(1).name);
    }

    @Test
    public void codeWirdUebernommenUndDoppelterVerworfen() {
        migrieren();
        List<Code> codes = db.codeDao().getAll();
        // '111' war doppelt vergeben und darf nur einmal existieren.
        assertEquals(2, codes.size());

        Code ersterCode = db.codeDao().findByWert("111");
        assertNotNull(ersterCode);
        assertEquals(1L, ersterCode.itemId);
        assertEquals(CodeType.QR, ersterCode.typ);

        Code zweiterCode = db.codeDao().findByWert("222");
        assertNotNull(zweiterCode);
        assertEquals(CodeType.BARCODE, zweiterCode.typ);
    }

    @Test
    public void verliehenerArtikelWirdZuOffenemVorgang() {
        migrieren();
        List<Verleih> offene = db.verleihDao().getOffene(2);
        assertEquals(1, offene.size());
        assertEquals("Max", offene.get(0).person);
        assertEquals(5000L, offene.get(0).ausgeliehenAm);
        assertNull(offene.get(0).zurueckAm);

        ItemWithState zustand = db.itemDao().getState(2);
        assertEquals(1, zustand.verliehen);
        assertEquals(0, zustand.vorhanden());
    }

    @Test
    public void verlorenerArtikelBehaeltSeineMenge() {
        migrieren();
        ItemWithState zustand = db.itemDao().getState(3);
        assertEquals(1, zustand.verloren());
        assertEquals(0, zustand.vorhanden());
    }

    @Test
    public void vorhandenerArtikelBehaeltBestand() {
        migrieren();
        ItemWithState zustand = db.itemDao().getState(1);
        assertEquals(2, zustand.vorhanden());
        assertEquals(0, zustand.verliehen);
        assertTrue(zustand.codes.size() >= 1);
    }
}
