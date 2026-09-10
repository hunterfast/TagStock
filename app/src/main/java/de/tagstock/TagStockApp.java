package de.tagstock;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;

import de.tagstock.util.Abgleichplaner;
import de.tagstock.util.Einstellungen;

/**
 * Haelt die App im Blick, waehrend niemand hinsieht: Sobald wieder eine
 * Verbindung besteht, wird ein Abgleich vorgemerkt. Was offline angelegt oder
 * geaendert wurde, geht dann von selbst zum Server.
 */
public class TagStockApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Einstellungen.anwenden(this);
        Abgleichplaner.regelmaessigPlanen(this);
        Abgleichplaner.vormerken(this);
        netzBeobachten();
    }

    /**
     * Meldet sich, wenn ein Netz nutzbar wird. WorkManager wartet zwar selbst
     * auf eine Verbindung, braucht dafuer aber seinen eigenen Takt - so geht es
     * sofort, solange die App laeuft.
     */
    private void netzBeobachten() {
        ConnectivityManager verwaltung = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (verwaltung == null) {
            return;
        }
        NetworkRequest bedingung = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        try {
            verwaltung.registerNetworkCallback(bedingung,
                    new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(@NonNull Network netz) {
                            Abgleichplaner.vormerken(TagStockApp.this);
                        }
                    });
        } catch (RuntimeException fehler) {
            // Auf manchen Geraeten ist die Anmeldung begrenzt - dann bleibt der
            // regelmaessige Auftrag als Rueckfallebene.
        }
    }
}
