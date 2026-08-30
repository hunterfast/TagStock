package de.tagstock.data;

import androidx.room.TypeConverter;

/** Wandelt die Codeart fuer die Ablage in der Datenbank um. */
public final class Converters {

    @TypeConverter
    public static String fromCodeType(CodeType type) {
        return type == null ? CodeType.MANUELL.name() : type.name();
    }

    @TypeConverter
    public static CodeType toCodeType(String name) {
        return CodeType.fromName(name);
    }
}
