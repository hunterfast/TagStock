package de.tagstock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.tagstock.R;
import de.tagstock.data.Artikel;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityMainBinding;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;

/**
 * Rahmen der App: unten die vier Bereiche Bestand, Scannen, Hinzufuegen und
 * Anfragen, oben Titel und Einstellungen.
 */
public class MainActivity extends AppCompatActivity {

    /** Bildschirme, die gescannte Codes selbst verarbeiten wollen. */
    public interface NfcEmpfaenger {
        void onNfcCode(ScanResult ergebnis);
    }

    private static final String TAG_BESTAND = "bestand";
    private static final String TAG_SCAN = "scan";
    private static final String TAG_NEU = "neu";
    private static final String TAG_ANFRAGEN = "anfragen";

    private ActivityMainBinding binding;
    private Repository repository;
    @Nullable
    private NfcEmpfaenger empfaenger;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Einstellungen.anwenden(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = Repository.getInstance(this);

        setSupportActionBar(binding.toolbar);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_bestand) {
                zeige(TAG_BESTAND);
            } else if (id == R.id.tab_scan) {
                zeige(TAG_SCAN);
            } else if (id == R.id.tab_neu) {
                zeige(TAG_NEU);
            } else if (id == R.id.tab_anfragen) {
                zeige(TAG_ANFRAGEN);
            }
            return true;
        });

        if (savedInstanceState == null) {
            zeige(TAG_BESTAND);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        titelAktualisieren();
        NfcHelper.enableReader(this, tag -> {
            ScanResult ergebnis = NfcHelper.read(tag);
            runOnUiThread(() -> nfcVerarbeiten(ergebnis));
        });
    }

    @Override
    protected void onPause() {
        NfcHelper.disableReader(this);
        super.onPause();
    }

    /** Der Scan-Bereich meldet sich an, solange er sichtbar ist. */
    public void setNfcEmpfaenger(@Nullable NfcEmpfaenger neuerEmpfaenger) {
        this.empfaenger = neuerEmpfaenger;
    }

    private void nfcVerarbeiten(ScanResult ergebnis) {
        if (empfaenger != null) {
            empfaenger.onNfcCode(ergebnis);
            return;
        }
        Toast.makeText(this, getString(R.string.nfc_tag_gelesen, ergebnis.code),
                Toast.LENGTH_SHORT).show();
        codeOeffnen(ergebnis);
    }

    /** Sucht den Artikel zum Code und oeffnet ihn - oder bietet das Anlegen an. */
    public void codeOeffnen(ScanResult ergebnis) {
        repository.findeNachKennung(ergebnis.werte(), treffer -> {
            if (treffer != null) {
                startActivity(ArtikelDetailActivity.intent(this, treffer.id));
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.treffer_nicht_gefunden_titel)
                    .setMessage(getString(R.string.treffer_nicht_gefunden_text, ergebnis.code))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.treffer_anlegen, (dialog, welcher) ->
                            neuMitKennung(ergebnis.code))
                    .show();
        });
    }

    /** Wechselt auf den Bereich "Hinzufuegen" und traegt die Kennung ein. */
    public void neuMitKennung(String kennung) {
        binding.bottomNavigation.setSelectedItemId(R.id.tab_neu);
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAG_NEU);
        if (fragment instanceof ArtikelFormFragment) {
            ((ArtikelFormFragment) fragment).kennungSetzen(kennung);
        }
    }

    /** Springt zurueck in den Bestand, etwa nach dem Anlegen. */
    public void zeigeBestand() {
        binding.bottomNavigation.setSelectedItemId(R.id.tab_bestand);
    }

    private void zeige(String tag) {
        FragmentTransaction transaktion = getSupportFragmentManager().beginTransaction();
        for (Fragment vorhanden : getSupportFragmentManager().getFragments()) {
            transaktion.hide(vorhanden);
        }
        Fragment ziel = getSupportFragmentManager().findFragmentByTag(tag);
        if (ziel == null) {
            ziel = erzeuge(tag);
            transaktion.add(R.id.fragmentContainer, ziel, tag);
        } else {
            transaktion.show(ziel);
        }
        transaktion.commit();
        titelAktualisieren();
    }

    private Fragment erzeuge(String tag) {
        switch (tag) {
            case TAG_SCAN:
                return new ScanFragment();
            case TAG_NEU:
                return ArtikelFormFragment.neu();
            case TAG_ANFRAGEN:
                return new AnfragenFragment();
            default:
                return new BestandFragment();
        }
    }

    private void titelAktualisieren() {
        if (getSupportActionBar() == null) {
            return;
        }
        getSupportActionBar().setTitle(R.string.app_name);
        String team = Einstellungen.teamName(this);
        getSupportActionBar().setSubtitle(
                Einstellungen.serverAktiv(this) && !team.isEmpty() ? team : null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_einstellungen) {
            startActivity(new Intent(this, EinstellungenActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Oeffnet einen Artikel aus einer der Listen. */
    public void artikelOeffnen(Artikel artikel) {
        startActivity(ArtikelDetailActivity.intent(this, artikel.id));
    }
}
