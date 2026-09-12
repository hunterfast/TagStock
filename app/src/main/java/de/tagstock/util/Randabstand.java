package de.tagstock.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Haelt die Bedienung frei von Statusleiste, Navigationsleiste und den
 * abgerundeten Ecken mancher Geraete.
 *
 * <p>Seit Android 15 zeichnen Apps grundsaetzlich unter die Systemleisten -
 * ohne Zutun liegen Titel und Knoepfe also unter Uhrzeit und WLAN-Symbol und
 * unten unter der Navigationsleiste. Hier kommt der noetige Abstand drauf,
 * jeweils zusaetzlich zu dem, was das Layout selbst schon vorsieht.</p>
 */
public final class Randabstand {

    /** Merkt sich den Abstand aus dem Layout, damit er nicht mitwaechst. */
    private static final class Grundabstand {
        final int links;
        final int oben;
        final int rechts;
        final int unten;

        Grundabstand(View flaeche) {
            links = flaeche.getPaddingLeft();
            oben = flaeche.getPaddingTop();
            rechts = flaeche.getPaddingRight();
            unten = flaeche.getPaddingBottom();
        }
    }

    private Randabstand() {
    }

    private static Insets leisten(WindowInsetsCompat fenster) {
        return fenster.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
    }

    /** Rundum Abstand halten - fuer die aeussere Flaeche eines Bildschirms. */
    public static void anwenden(View wurzel) {
        Grundabstand grund = new Grundabstand(wurzel);
        ViewCompat.setOnApplyWindowInsetsListener(wurzel, (flaeche, fenster) -> {
            Insets rand = leisten(fenster);
            Insets tastatur = fenster.getInsets(WindowInsetsCompat.Type.ime());
            // Ist die Tastatur offen, zaehlt sie unten statt der Navigationsleiste.
            flaeche.setPadding(grund.links + rand.left, grund.oben + rand.top,
                    grund.rechts + rand.right,
                    grund.unten + Math.max(rand.bottom, tastatur.bottom));
            return fenster;
        });
        ViewCompat.requestApplyInsets(wurzel);
    }

    /**
     * Fuer Bildschirme mit einer festen Leiste am unteren Rand.
     *
     * <p>Oeffnet sich die Tastatur, schoebe der gewoehnliche Abstand die
     * Leiste ueber die Tastatur - und damit genau vor das Feld, in das man
     * gerade tippt. Die Leiste tritt deshalb beiseite, solange getippt wird,
     * und der Inhalt bekommt den frei gewordenen Platz. Danach ist sie wieder
     * da.</p>
     */
    public static void mitLeiste(View wurzel, View leiste, View inhalt) {
        Grundabstand grundWurzel = new Grundabstand(wurzel);
        Grundabstand grundLeiste = new Grundabstand(leiste);
        Grundabstand grundInhalt = new Grundabstand(inhalt);
        ViewCompat.setOnApplyWindowInsetsListener(wurzel, (flaeche, fenster) -> {
            Insets rand = leisten(fenster);
            // Nicht die Hoehe fragen, sondern ob sie da ist: Hat das System
            // das Fenster schon verkleinert, ist die Hoehe 0 - offen ist sie
            // trotzdem.
            boolean tippen = fenster.isVisible(WindowInsetsCompat.Type.ime());
            int tastatur = fenster.getInsets(WindowInsetsCompat.Type.ime()).bottom;

            // Unten haelt nicht die Wurzel Abstand, sondern die Leiste selbst.
            flaeche.setPadding(grundWurzel.links + rand.left, grundWurzel.oben + rand.top,
                    grundWurzel.rechts + rand.right, grundWurzel.unten);

            leiste.setVisibility(tippen ? View.GONE : View.VISIBLE);
            leiste.setPadding(grundLeiste.links, grundLeiste.oben,
                    grundLeiste.rechts, grundLeiste.unten + rand.bottom);

            // Waehrend die Leiste weg ist, muss der Inhalt den Abstand nach
            // unten selbst halten - zur Tastatur oder zur Navigationsleiste.
            inhalt.setPadding(grundInhalt.links, grundInhalt.oben, grundInhalt.rechts,
                    grundInhalt.unten + (tippen ? tastatur : 0));
            return fenster;
        });
        ViewCompat.requestApplyInsets(wurzel);
    }

    /**
     * Fuer Bildschirme, die den ganzen Platz brauchen - etwa die Kamera: nur
     * die Bedienelemente ruecken ein, das Bild bleibt randlos.
     */
    public static void obenUnten(View oben, View unten) {
        Grundabstand grundOben = new Grundabstand(oben);
        ViewCompat.setOnApplyWindowInsetsListener(oben, (flaeche, fenster) -> {
            Insets rand = leisten(fenster);
            flaeche.setPadding(grundOben.links + rand.left, grundOben.oben + rand.top,
                    grundOben.rechts + rand.right, grundOben.unten);
            return fenster;
        });

        Grundabstand grundUnten = new Grundabstand(unten);
        ViewCompat.setOnApplyWindowInsetsListener(unten, (flaeche, fenster) -> {
            Insets rand = leisten(fenster);
            flaeche.setPadding(grundUnten.links + rand.left, grundUnten.oben,
                    grundUnten.rechts + rand.right, grundUnten.unten + rand.bottom);
            return fenster;
        });
        ViewCompat.requestApplyInsets(oben);
        ViewCompat.requestApplyInsets(unten);
    }
}
