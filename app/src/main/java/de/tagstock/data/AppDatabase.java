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
        entities = {Artikel.class, Kategorie.class, Protokoll.class},
        version = 5,
        exportSchema = true)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    public static final String DB_NAME = "tagstock.db";

    /** Vorschlaege, solange der Nutzer keine eigenen Kategorien gepflegt hat. */
    static final String[] STANDARD_KATEGORIEN = {
            "Elektronik", "Werkzeug", "Büromaterial", "Lager",
            "Verbrauchsmaterial", "Möbel", "3D Drucker", "Sonstiges"};

    private static volatile AppDatabase instance;

    public abstract ArtikelDao artikelDao();

    public abstract KategorieDao kategorieDao();

    public abstract ProtokollDao protokollDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                                    MIGRATION_4_5)
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    kategorienAnlegen(db);
                                }
                            })
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

    private static void kategorienAnlegen(SupportSQLiteDatabase db) {
        for (int i = 0; i < STANDARD_KATEGORIEN.length; i++) {
            db.execSQL("INSERT OR IGNORE INTO `kategorien` (`name`, `reihenfolge`) VALUES (?, ?)",
                    new Object[]{STANDARD_KATEGORIEN[i], i});
        }
    }

    /**
     * Version 1 hatte Code und Ausleihe direkt am Artikel. Version 2 zieht beides
     * in eigene Tabellen um: ein Artikel kann mehrere Codes haben, und Ausleihen
     * bleiben als Historie erhalten.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
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

    /**
     * Version 3 folgt dem Aufbau der Weboberflaeche: ein Artikel, ein Status,
     * Standort und Lagerort als Text, eine Kennung je Artikel. Aus dem Lager wird
     * der Standort, aus dem ersten Code die Kennung, aus offenen Ausleihen der
     * Status "verliehen". Die bisherige Ausleih-Historie wandert ins Protokoll.
     *
     * <p>Stueckzahlen entfallen: Ein Artikel mit Menge 5 wird zu einem Artikel.
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("PRAGMA defer_foreign_keys = TRUE");

            db.execSQL("CREATE TABLE IF NOT EXISTS `artikel` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`serverId` TEXT, "
                    + "`teamId` TEXT, "
                    + "`rfidUid` TEXT, "
                    + "`name` TEXT NOT NULL, "
                    + "`beschreibung` TEXT, "
                    + "`kategorie` TEXT, "
                    + "`standort` TEXT, "
                    + "`lagerort` TEXT, "
                    + "`fotoPfad` TEXT, "
                    + "`bildUrl` TEXT, "
                    + "`status` TEXT NOT NULL, "
                    + "`verliehenAn` TEXT, "
                    + "`rueckgabeDatum` INTEGER, "
                    + "`zuletztGescannt` INTEGER, "
                    + "`scanWarnung` TEXT NOT NULL, "
                    + "`erstelltAm` INTEGER NOT NULL, "
                    + "`geaendertAm` INTEGER NOT NULL, "
                    + "`offen` INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artikel_rfidUid`"
                    + " ON `artikel` (`rfidUid`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_artikel_serverId`"
                    + " ON `artikel` (`serverId`)");

            db.execSQL("INSERT INTO `artikel` (`id`, `rfidUid`, `name`, `beschreibung`,"
                    + " `standort`, `lagerort`, `fotoPfad`, `status`, `verliehenAn`,"
                    + " `scanWarnung`, `erstelltAm`, `geaendertAm`, `offen`)"
                    + " SELECT i.`id`,"
                    + " (SELECT c.`wert` FROM `codes` c WHERE c.`itemId` = i.`id`"
                    + "   ORDER BY c.`erfasstAm` ASC LIMIT 1),"
                    + " i.`name`,"
                    + " TRIM(COALESCE(i.`beschreibung`, '')"
                    + "   || CASE WHEN i.`notiz` IS NOT NULL AND TRIM(i.`notiz`) != ''"
                    + "           THEN CHAR(10) || i.`notiz` ELSE '' END),"
                    + " (SELECT l.`name` FROM `lager` l WHERE l.`id` = i.`lagerId`),"
                    + " (SELECT l.`ort` FROM `lager` l WHERE l.`id` = i.`lagerId`),"
                    + " i.`fotoPfad`,"
                    + " CASE"
                    + "   WHEN EXISTS (SELECT 1 FROM `verleih` v WHERE v.`itemId` = i.`id`"
                    + "                AND v.`zurueckAm` IS NULL) THEN 'verliehen'"
                    + "   WHEN i.`menge` > 0 AND i.`mengeVerloren` >= i.`menge`"
                    + "        THEN 'nicht vorhanden'"
                    + "   ELSE 'vorhanden' END,"
                    + " (SELECT v.`person` FROM `verleih` v WHERE v.`itemId` = i.`id`"
                    + "   AND v.`zurueckAm` IS NULL ORDER BY v.`ausgeliehenAm` ASC LIMIT 1),"
                    + " '1j', i.`erstelltAm`, i.`geaendertAm`, 1"
                    + " FROM `items` i");

            db.execSQL("CREATE TABLE IF NOT EXISTS `kategorien` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`serverId` TEXT, "
                    + "`teamId` TEXT, "
                    + "`name` TEXT NOT NULL, "
                    + "`reihenfolge` INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_kategorien_name`"
                    + " ON `kategorien` (`name`)");
            kategorienAnlegen(db);

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

            // Bisherige Ausleihen als Protokolleintraege erhalten.
            db.execSQL("INSERT INTO `protokoll` (`artikelId`, `artikelName`, `aktion`,"
                    + " `alterWert`, `neuerWert`, `nutzer`, `zeitpunkt`, `offen`)"
                    + " SELECT v.`itemId`, COALESCE(i.`name`, ''), 'Verliehen', NULL,"
                    + " v.`person`, NULL, v.`ausgeliehenAm`, 1"
                    + " FROM `verleih` v LEFT JOIN `items` i ON i.`id` = v.`itemId`");
            db.execSQL("INSERT INTO `protokoll` (`artikelId`, `artikelName`, `aktion`,"
                    + " `alterWert`, `neuerWert`, `nutzer`, `zeitpunkt`, `offen`)"
                    + " SELECT v.`itemId`, COALESCE(i.`name`, ''), 'Zurückgenommen',"
                    + " v.`person`, NULL, NULL, v.`zurueckAm`, 1"
                    + " FROM `verleih` v LEFT JOIN `items` i ON i.`id` = v.`itemId`"
                    + " WHERE v.`zurueckAm` IS NOT NULL");

            db.execSQL("DROP TABLE `codes`");
            db.execSQL("DROP TABLE `verleih`");
            db.execSQL("DROP TABLE `items`");
            db.execSQL("DROP TABLE `lager`");
        }
    };

    /**
     * Behaelter: Ein Artikel kann selbst etwas aufnehmen - eine Box, eine
     * Schublade, ein Regal -, und jeder Artikel kann in einem solchen liegen.
     * Wer drin liegt, merkt sich die Kennung des Behaelters und nicht dessen
     * laufende Nummer: Die Kennung klebt am Moebel, wird beim Einraeumen
     * gescannt und bedeutet auf jedem Geraet dasselbe.
     */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `artikel` ADD COLUMN `istBehaelter`"
                    + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `artikel` ADD COLUMN `behaelterArt` TEXT");
            db.execSQL("ALTER TABLE `artikel` ADD COLUMN `behaelterKennung` TEXT");
        }
    };

    /**
     * Mengen und Verpackungseinheiten. Was es bisher gab, ist genau ein
     * Stueck - deshalb steht die Menge auf 1 und nicht auf 0.
     */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `artikel` ADD COLUMN `menge`"
                    + " INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE `artikel` ADD COLUMN `istVerpackung`"
                    + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `artikel` ADD COLUMN `packungsGroesse`"
                    + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `artikel` ADD COLUMN `angebrochen` TEXT");
        }
    };
}
