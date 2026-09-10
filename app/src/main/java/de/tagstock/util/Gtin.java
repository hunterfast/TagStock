package de.tagstock.util;

import androidx.annotation.Nullable;

/**
 * Prueft Handelsnummern (GTIN/EAN/UPC) ohne Netz: stimmt die Pruefziffer, und
 * was fuer eine Nummer ist es ueberhaupt? Das erkennt Tippfehler beim
 * Abschreiben und unterscheidet echte Herstellernummern von hausinternen
 * Nummernkreisen, die es in keiner Datenbank gibt.
 */
public final class Gtin {

    /** Praefixbereiche nach GS1. Nur die erste Stelle bis dritte Stelle zaehlt. */
    private static final int[][] BEREICHE = {
            {0, 19, 0}, {20, 29, 1}, {30, 39, 0}, {40, 49, 1}, {50, 59, 2}, {60, 139, 0},
            {200, 299, 1},
            {300, 379, 3}, {380, 380, 4}, {383, 383, 5}, {385, 385, 6}, {387, 387, 7},
            {389, 389, 8}, {390, 390, 9},
            {400, 440, 10}, {450, 459, 11}, {460, 469, 12}, {470, 470, 13}, {471, 471, 14},
            {474, 474, 15}, {475, 475, 16}, {476, 476, 17}, {477, 477, 18}, {478, 478, 19},
            {479, 479, 20}, {480, 480, 21}, {481, 481, 22}, {482, 482, 23}, {483, 483, 24},
            {484, 484, 25}, {485, 485, 26}, {486, 486, 27}, {487, 487, 28}, {488, 488, 29},
            {489, 489, 30},
            {490, 499, 11}, {500, 509, 31}, {520, 521, 32}, {528, 528, 33}, {529, 529, 34},
            {530, 530, 35}, {531, 531, 36}, {535, 535, 37}, {539, 539, 38}, {540, 549, 39},
            {560, 560, 40}, {569, 569, 41}, {570, 579, 42}, {590, 590, 43}, {594, 594, 44},
            {599, 599, 45}, {600, 601, 46}, {640, 649, 47}, {690, 699, 48}, {700, 709, 49},
            {729, 729, 50}, {730, 739, 51}, {750, 750, 52}, {754, 755, 53}, {760, 769, 54},
            {770, 771, 55}, {773, 773, 56}, {775, 775, 57}, {777, 777, 58}, {779, 779, 59},
            {780, 780, 60}, {784, 784, 61}, {786, 786, 62}, {789, 790, 63},
            {800, 839, 64}, {840, 849, 65}, {858, 858, 66}, {859, 859, 67}, {860, 860, 68},
            {865, 865, 69}, {867, 867, 70}, {868, 869, 71}, {870, 879, 72}, {880, 880, 73},
            {884, 884, 74}, {885, 885, 75}, {888, 888, 76}, {890, 890, 77}, {893, 893, 78},
            {896, 896, 79}, {899, 899, 80},
            {900, 919, 81}, {930, 939, 82}, {940, 949, 83}, {955, 955, 84}, {958, 958, 85},
            {960, 969, 86}, {977, 977, 87}, {978, 979, 88}, {980, 980, 89}, {981, 984, 2},
            {990, 999, 2},
    };

    private static final String[] NAMEN = {
            "USA und Kanada", "hausinterner Nummernkreis", "Gutschein", "Frankreich",
            "Bulgarien", "Slowenien", "Kroatien", "Bosnien-Herzegowina", "Montenegro",
            "Kosovo", "Deutschland", "Japan", "Russland", "Kirgisistan", "Taiwan",
            "Estland", "Lettland", "Aserbaidschan", "Litauen", "Usbekistan", "Sri Lanka",
            "Philippinen", "Belarus", "Ukraine", "Turkmenistan", "Moldau", "Armenien",
            "Georgien", "Kasachstan", "Tadschikistan", "Hongkong", "Grossbritannien",
            "Griechenland", "Libanon", "Zypern", "Albanien", "Nordmazedonien", "Malta",
            "Irland", "Belgien und Luxemburg", "Portugal", "Island", "Daenemark", "Polen",
            "Rumaenien", "Ungarn", "Suedafrika", "Finnland", "China", "Norwegen", "Israel",
            "Schweden", "Mexiko", "Kanada", "Schweiz", "Kolumbien", "Uruguay", "Peru",
            "Bolivien", "Argentinien", "Chile", "Paraguay", "Ecuador", "Brasilien",
            "Italien", "Spanien", "Kuba", "Tschechien", "Serbien", "Mongolei", "Nordkorea",
            "Tuerkei", "Niederlande", "Suedkorea", "Kambodscha", "Thailand", "Singapur",
            "Indien", "Vietnam", "Pakistan", "Indonesien", "Oesterreich", "Australien",
            "Neuseeland", "Malaysia", "Macau", "GS1 (Kurznummer)", "Zeitschrift (ISSN)",
            "Buch (ISBN)", "Pfand oder Erstattung",
    };

    private Gtin() {
    }

    /** true, wenn der Wert von der Laenge her eine Handelsnummer sein kann. */
    public static boolean moeglich(@Nullable String code) {
        if (code == null) {
            return false;
        }
        int laenge = code.length();
        if (laenge != 8 && laenge != 12 && laenge != 13 && laenge != 14) {
            return false;
        }
        for (int i = 0; i < laenge; i++) {
            if (code.charAt(i) < '0' || code.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /** Pruefziffer nach GS1: gewichtete Quersumme, aufgefuellt auf ein Vielfaches von zehn. */
    public static boolean pruefzifferStimmt(@Nullable String code) {
        if (!moeglich(code)) {
            return false;
        }
        int summe = 0;
        int gewicht = 3;
        for (int i = code.length() - 2; i >= 0; i--) {
            summe += (code.charAt(i) - '0') * gewicht;
            gewicht = gewicht == 3 ? 1 : 3;
        }
        int erwartet = (10 - (summe % 10)) % 10;
        return erwartet == code.charAt(code.length() - 1) - '0';
    }

    /** Art der Nummer, etwa "EAN-13" - oder null, wenn es keine Handelsnummer ist. */
    @Nullable
    public static String art(@Nullable String code) {
        if (!moeglich(code)) {
            return null;
        }
        switch (code.length()) {
            case 8:
                return "EAN-8";
            case 12:
                return "UPC-A";
            case 13:
                return "EAN-13";
            default:
                return "GTIN-14";
        }
    }

    /**
     * Land oder Zweck hinter dem Praefix. Achtung: das ist das Land der
     * GS1-Organisation, nicht zwingend das Herstellungsland.
     */
    @Nullable
    public static String herkunft(@Nullable String code) {
        if (!moeglich(code) || code.length() == 8) {
            return null;
        }
        // UPC-A wird fuer die Praefixsuche wie eine EAN-13 mit fuehrender Null gelesen.
        String dreizehn = code.length() == 12 ? "0" + code
                : code.length() == 14 ? code.substring(1) : code;
        int praefix = Integer.parseInt(dreizehn.substring(0, 3));
        for (int[] bereich : BEREICHE) {
            if (praefix >= bereich[0] && praefix <= bereich[1]) {
                return NAMEN[bereich[2]];
            }
        }
        return null;
    }

    /** true, wenn die Nummer aus einem hausinternen Kreis stammt. */
    public static boolean intern(@Nullable String code) {
        return "hausinterner Nummernkreis".equals(herkunft(code));
    }
}
