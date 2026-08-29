package de.tagstock.data;

import androidx.room.TypeConverter;

/** Wandelt die Enums fuer die Ablage in der Datenbank um. */
public final class Converters {

    @TypeConverter
    public static String fromStatus(ItemStatus status) {
        return status == null ? ItemStatus.VORHANDEN.name() : status.name();
    }

    @TypeConverter
    public static ItemStatus toStatus(String name) {
        return ItemStatus.fromName(name);
    }

    @TypeConverter
    public static String fromCodeType(CodeType type) {
        return type == null ? CodeType.KEINER.name() : type.name();
    }

    @TypeConverter
    public static CodeType toCodeType(String name) {
        return CodeType.fromName(name);
    }
}
