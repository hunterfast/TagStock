package de.tagstock.data;

import androidx.room.TypeConverter;

/** Wandelt die Aufzaehlungen fuer die Ablage in der Datenbank um. */
public final class Converters {

    @TypeConverter
    public static String vonStatus(ArtikelStatus status) {
        return status == null ? ArtikelStatus.VORHANDEN.schluessel : status.schluessel;
    }

    @TypeConverter
    public static ArtikelStatus zuStatus(String wert) {
        return ArtikelStatus.vonSchluessel(wert);
    }

    @TypeConverter
    public static String vonScanWarnung(ScanWarnung warnung) {
        return warnung == null ? ScanWarnung.JAHR.schluessel : warnung.schluessel;
    }

    @TypeConverter
    public static ScanWarnung zuScanWarnung(String wert) {
        return ScanWarnung.vonSchluessel(wert);
    }
}
