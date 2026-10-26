package de.tagstock;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import androidx.annotation.NonNull;

import de.tagstock.util.Abgleichplaner;
import de.tagstock.util.Dauerabgleich;
import de.tagstock.util.Einstellungen;

/**
 * Haelt die Geraete auf demselben Stand: Sobald eine Verbindung dazukommt -
 * WLAN, Mobilfunk oder VPN -, wird abgeglichen; solange die App offen ist,
 * alle 30 Sekunden; ist sie zu, stuendlich im Hintergrund. Was offline
 * angelegt oder geaendert wurde, geht so von selbst zum Server, und was die
 * anderen geaendert haben, kommt von selbst hierher.
 */
public class TagStockApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Einstellungen.anwenden(this);
        Abgleichplaner.regelmaessigPlanen(this);
        Dauerabgleich.anmelden(this);
        netzBeobachten();
    }

    /**
     * Meldet sich, wenn ein anderes Netz das Sagen hat - auch beim Einschalten
     * des VPN: Fuer die App wird es dann zum Netz, ueber das alles laeuft.
     * Android weckt eine geschlossene App dafuer nicht; das hier gilt, solange
     * sie im Speicher ist. Gleich beim Anmelden kommt die erste Meldung fuer
     * das Netz, das gerade da ist - das ist zugleich der Abgleich beim Start.
     */
    private void netzBeobachten() {
        ConnectivityManager verwaltung = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (verwaltung == null) {
            return;
        }
        try {
            verwaltung.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network netz) {
                    // Ist die App offen, gleicht sie selbst ab; sonst WorkManager.
                    if (!Dauerabgleich.netzDa()) {
                        Abgleichplaner.netzDa(TagStockApp.this);
                    }
                }
            });
        } catch (RuntimeException fehler) {
            // Auf manchen Geraeten ist die Anmeldung begrenzt - dann bleibt der
            // regelmaessige Auftrag als Rueckfallebene.
        }
    }
}
