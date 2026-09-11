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
