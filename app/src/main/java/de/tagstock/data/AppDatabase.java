package de.tagstock.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {Lager.class, Item.class, Code.class, Verleih.class},
        version = 2,
        exportSchema = true)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DB_NAME = "tagstock.db";
    private static volatile AppDatabase instance;

    public abstract LagerDao lagerDao();

    public abstract ItemDao itemDao();

    public abstract CodeDao codeDao();

    public abstract VerleihDao verleihDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }

    /** Nur fuer Tests: gesetzte Instanz verwerfen. */
    public static void resetInstance() {
        synchronized (AppDatabase.class) {
            instance = null;
        }
    }

    /**
     * Version 1 hatte Code und Ausleihe direkt am Artikel. Version 2 zieht beides
     * in eigene Tabellen um: ein Artikel kann mehrere Codes haben, und Ausleihen
     * bleiben als Historie erhalten. Der Status wird nicht mehr gespeichert,
     * sondern aus den Stueckzahlen abgeleitet.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Fremdschluessel erst am Ende der Transaktion pruefen, weil die
            // Artikeltabelle zwischendurch neu aufgebaut wird.
            db.execSQL("PRAGMA defer_foreign_keys = TRUE");

            db.execSQL("CREATE TABLE IF NOT EXISTS `codes` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`itemId` INTEGER NOT NULL, "
                    + "`wert` TEXT NOT NULL, "
                    + "`typ` TEXT NOT NULL, "
                    + "`erfasstAm` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_codes_wert` ON `codes` (`wert`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_codes_itemId` ON `codes` (`itemId`)");

            // Bisherige Codes uebernehmen. Doppelt vergebene Werte fallen dabei
            // weg - genau das soll der eindeutige Index kuenftig verhindern.
            db.execSQL("INSERT OR IGNORE INTO `codes` (`itemId`, `wert`, `typ`, `erfasstAm`)"
                    + " SELECT `id`, `code`,"
                    + " CASE WHEN `codeType` IN ('BARCODE', 'QR', 'NFC', 'MANUELL')"
                    + "      THEN `codeType` ELSE 'MANUELL' END,"
                    + " `erstelltAm` FROM `items`"
                    + " WHERE `code` IS NOT NULL AND TRIM(`code`) != ''");

            db.execSQL("CREATE TABLE IF NOT EXISTS `verleih` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`itemId` INTEGER NOT NULL, "
                    + "`person` TEXT NOT NULL, "
                    + "`menge` INTEGER NOT NULL, "
                    + "`ausgeliehenAm` INTEGER NOT NULL, "
                    + "`zurueckAm` INTEGER, "
                    + "`notiz` TEXT, "
                    + "FOREIGN KEY(`itemId`) REFERENCES `items`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_verleih_itemId` ON `verleih` (`itemId`)");

            // Laufende Ausleihen als offenen Vorgang uebernehmen.
            db.execSQL("INSERT INTO `verleih`"
                    + " (`itemId`, `person`, `menge`, `ausgeliehenAm`, `zurueckAm`, `notiz`)"
                    + " SELECT `id`,"
                    + " COALESCE(NULLIF(TRIM(`verliehenAn`), ''), 'Unbekannt'),"
                    + " MAX(`menge`, 1),"
                    + " COALESCE(`verliehenSeit`, `erstelltAm`), NULL, NULL"
                    + " FROM `items` WHERE `status` = 'VERLIEHEN'");

            db.execSQL("CREATE TABLE IF NOT EXISTS `items_neu` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`lagerId` INTEGER NOT NULL, "
                    + "`name` TEXT NOT NULL, "
                    + "`beschreibung` TEXT, "
                    + "`menge` INTEGER NOT NULL, "
                    + "`mengeVerloren` INTEGER NOT NULL, "
                    + "`fotoPfad` TEXT, "
                    + "`notiz` TEXT, "
                    + "`erstelltAm` INTEGER NOT NULL, "
                    + "`geaendertAm` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`lagerId`) REFERENCES `lager`(`id`)"
                    + " ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("INSERT INTO `items_neu`"
                    + " (`id`, `lagerId`, `name`, `beschreibung`, `menge`, `mengeVerloren`,"
                    + "  `fotoPfad`, `notiz`, `erstelltAm`, `geaendertAm`)"
                    + " SELECT `id`, `lagerId`, `name`, `beschreibung`, MAX(`menge`, 0),"
                    + " CASE WHEN `status` = 'VERLOREN' THEN MAX(`menge`, 1) ELSE 0 END,"
                    + " NULL, `notiz`, `erstelltAm`, `geaendertAm` FROM `items`");

            db.execSQL("DROP TABLE `items`");
            db.execSQL("ALTER TABLE `items_neu` RENAME TO `items`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_lagerId` ON `items` (`lagerId`)");
        }
    };
}
