package de.tagstock.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import de.tagstock.R;
import de.tagstock.data.Bestand;
import de.tagstock.data.Lager;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityMainBinding;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;
import de.tagstock.util.Sicherung;

/** Startbildschirm: Uebersicht aller Lager, Scan-Einstieg, Suche und Sicherung. */
public class MainActivity extends AppCompatActivity implements LagerAdapter.Listener {

    private ActivityMainBinding binding;
    private Repository repository;
    private LagerAdapter adapter;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    ScanResult scan = ScanResult.fromIntent(result.getData());
                    if (scan != null) {
                        scanVerarbeiten(scan);
                    }
                }
            });

    private final ActivityResultLauncher<String> jsonExport = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> exportieren(uri, true));

    private final ActivityResultLauncher<String> csvExport = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            uri -> exportieren(uri, false));

    private final ActivityResultLauncher<String[]> importAuswahl = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importieren);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = Repository.getInstance(this);

        setSupportActionBar(binding.toolbar);

        adapter = new LagerAdapter(this);
        binding.recyclerLager.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerLager.setAdapter(adapter);

        binding.fabScan.setOnClickListener(v -> scanLauncher.launch(ScannerActivity.createIntent(this)));

        LagerListViewModel viewModel = new ViewModelProvider(this).get(LagerListViewModel.class);
        viewModel.getLager().observe(this, lager -> {
            adapter.submitList(lager);
            boolean leer = lager == null || lager.isEmpty();
            binding.emptyView.setVisibility(leer ? View.VISIBLE : View.GONE);
            binding.recyclerLager.setVisibility(leer ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Auch auf dem Startbildschirm koennen NFC-Tags direkt gelesen werden.
        NfcHelper.enableReader(this, tag -> {
            ScanResult scan = NfcHelper.read(tag);
            runOnUiThread(() -> {
                Toast.makeText(this, getString(R.string.nfc_tag_gelesen, scan.code),
                        Toast.LENGTH_SHORT).show();
                scanVerarbeiten(scan);
            });
        });
    }

    @Override
    protected void onPause() {
        NfcHelper.disableReader(this);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_lager) {
            LagerDialog.show(this, repository, null, null);
            return true;
        }
        if (id == R.id.action_suche) {
            startActivity(new Intent(this, SucheActivity.class));
            return true;
        }
        if (id == R.id.action_export_json) {
            jsonExport.launch(Sicherung.dateiname("json"));
            return true;
        }
        if (id == R.id.action_export_csv) {
            csvExport.launch(Sicherung.dateiname("csv"));
            return true;
        }
        if (id == R.id.action_import) {
            importAuswahl.launch(new String[]{"application/json", "text/plain", "*/*"});
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLagerClick(Lager lager) {
        startActivity(LagerDetailActivity.createIntent(this, lager.id));
    }

    @Override
    public void onLagerLongClick(Lager lager) {
        String[] optionen = {getString(R.string.action_edit), getString(R.string.action_delete)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(lager.name)
                .setItems(optionen, (dialog, welcher) -> {
                    if (welcher == 0) {
                        LagerDialog.show(this, repository, lager, null);
                    } else {
                        lagerLoeschen(lager);
                    }
                })
                .show();
    }

    private void lagerLoeschen(Lager lager) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lager_loeschen_titel)
                .setMessage(getString(R.string.lager_loeschen_text, lager.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, welcher) -> {
                    repository.deleteLager(lager);
                    Toast.makeText(this, R.string.lager_geloescht, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // --------------------------------------------------------------------- Scan

    /** Gescannten Code ueber alle Lager hinweg suchen. */
    private void scanVerarbeiten(ScanResult scan) {
        repository.findeZuCode(scan.werte(), treffer -> {
            if (treffer != null) {
                Toast.makeText(this, getString(R.string.treffer_gefunden, treffer.item.item.name),
                        Toast.LENGTH_SHORT).show();
                startActivity(ItemEditActivity.editIntent(this, treffer.item.item.id));
            } else {
                artikelAnlegenFragen(scan);
            }
        });
    }

    private void artikelAnlegenFragen(ScanResult scan) {
        repository.loadAllLager(lagerListe -> {
            if (lagerListe.isEmpty()) {
                Toast.makeText(this, R.string.treffer_kein_lager, Toast.LENGTH_LONG).show();
                return;
            }
            if (lagerListe.size() == 1) {
                Lager ziel = lagerListe.get(0);
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.treffer_nicht_gefunden_titel)
                        .setMessage(getString(R.string.treffer_nicht_gefunden_text, scan.code))
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.treffer_anlegen, (dialog, welcher) ->
                                startActivity(ItemEditActivity.newIntent(this, ziel.id, scan)))
                        .show();
                return;
            }
            lagerAuswahl(lagerListe, scan);
        });
    }

    private void lagerAuswahl(List<Lager> lagerListe, ScanResult scan) {
        String[] namen = new String[lagerListe.size()];
        for (int i = 0; i < lagerListe.size(); i++) {
            namen[i] = lagerListe.get(i).name;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lager_auswaehlen)
                .setItems(namen, (dialog, welcher) ->
                        startActivity(ItemEditActivity.newIntent(this, lagerListe.get(welcher).id, scan)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- Sicherung

    private void exportieren(@Nullable Uri ziel, boolean alsJson) {
        if (ziel == null) {
            return;
        }
        repository.ladeAlles(bestand -> Hintergrund.starte(
                () -> {
                    String inhalt = alsJson ? Sicherung.alsJson(bestand) : Sicherung.alsCsv(bestand);
                    Sicherung.schreibe(this, ziel, inhalt);
                    return bestand.items.size();
                },
                anzahl -> Toast.makeText(this, getString(R.string.export_fertig, anzahl),
                        Toast.LENGTH_LONG).show(),
                fehler -> Toast.makeText(this, getString(R.string.export_fehler,
                        String.valueOf(fehler.getMessage())), Toast.LENGTH_LONG).show()));
    }

    private void importieren(@Nullable Uri quelle) {
        if (quelle == null) {
            return;
        }
        Hintergrund.starte(
                () -> Sicherung.ausJson(Sicherung.lies(this, quelle)),
                bestand -> {
                    if (bestand.istLeer()) {
                        Toast.makeText(this, R.string.import_leer, Toast.LENGTH_LONG).show();
                        return;
                    }
                    importArtFragen(bestand);
                },
                fehler -> Toast.makeText(this, getString(R.string.import_fehler,
                        String.valueOf(fehler.getMessage())), Toast.LENGTH_LONG).show());
    }

    private void importArtFragen(Bestand bestand) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_titel)
                .setMessage(getString(R.string.import_frage,
                        bestand.lager.size(), bestand.items.size()))
                .setNeutralButton(R.string.action_cancel, null)
                .setNegativeButton(R.string.import_ergaenzen, (dialog, welcher) ->
                        repository.ergaenzeUm(bestand, anzahl -> Toast.makeText(this,
                                getString(R.string.import_ergaenzt, anzahl),
                                Toast.LENGTH_LONG).show()))
                .setPositiveButton(R.string.import_ersetzen, (dialog, welcher) ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.import_ersetzen_titel)
                                .setMessage(R.string.import_ersetzen_text)
                                .setNegativeButton(R.string.action_cancel, null)
                                .setPositiveButton(R.string.import_ersetzen, (d, w) ->
                                        repository.ersetzeAlles(bestand, erfolg ->
                                                Toast.makeText(this, R.string.import_fertig,
                                                        Toast.LENGTH_LONG).show()))
                                .show())
                .show();
    }
}
