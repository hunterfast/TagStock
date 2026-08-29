package de.tagstock.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
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
import de.tagstock.data.Lager;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityMainBinding;
import de.tagstock.databinding.DialogLagerEditBinding;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;

/** Startbildschirm: Uebersicht aller Lager und Einstieg in den Scanner. */
public class MainActivity extends AppCompatActivity implements LagerAdapter.Listener {

    private ActivityMainBinding binding;
    private Repository repository;
    private LagerAdapter adapter;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    ScanResult scan = ScanResult.fromIntent(result.getData());
                    if (scan != null) {
                        handleScan(scan);
                    }
                }
            });

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
                handleScan(scan);
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
        if (item.getItemId() == R.id.action_add_lager) {
            showLagerDialog(null);
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
                .setItems(optionen, (dialog, which) -> {
                    if (which == 0) {
                        showLagerDialog(lager);
                    } else {
                        confirmDeleteLager(lager);
                    }
                })
                .show();
    }

    private void confirmDeleteLager(Lager lager) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lager_loeschen_titel)
                .setMessage(getString(R.string.lager_loeschen_text, lager.name))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.deleteLager(lager);
                    Toast.makeText(this, R.string.lager_geloescht, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** Dialog zum Anlegen (lager == null) oder Bearbeiten eines Lagers. */
    private void showLagerDialog(@Nullable Lager lager) {
        DialogLagerEditBinding dialogBinding = DialogLagerEditBinding.inflate(LayoutInflater.from(this));
        if (lager != null) {
            dialogBinding.editLagerName.setText(lager.name);
            dialogBinding.editLagerOrt.setText(lager.ort);
            dialogBinding.editLagerBeschreibung.setText(lager.beschreibung);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(lager == null ? R.string.lager_neu : R.string.lager_bearbeiten)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = value(dialogBinding.editLagerName.getText());
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.lager_name_fehlt, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String ort = value(dialogBinding.editLagerOrt.getText());
                    String beschreibung = value(dialogBinding.editLagerBeschreibung.getText());

                    if (lager == null) {
                        repository.insertLager(new Lager(name, nullIfEmpty(beschreibung), nullIfEmpty(ort)),
                                id -> Toast.makeText(this, R.string.lager_gespeichert,
                                        Toast.LENGTH_SHORT).show());
                    } else {
                        lager.name = name;
                        lager.ort = nullIfEmpty(ort);
                        lager.beschreibung = nullIfEmpty(beschreibung);
                        repository.updateLager(lager);
                        Toast.makeText(this, R.string.lager_gespeichert, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** Gescannten Code ueber alle Lager hinweg suchen. */
    private void handleScan(ScanResult scan) {
        repository.findByCode(scan.code, item -> {
            if (item != null) {
                Toast.makeText(this, getString(R.string.treffer_gefunden, item.name),
                        Toast.LENGTH_SHORT).show();
                startActivity(ItemEditActivity.editIntent(this, item.id));
            } else {
                askCreateItem(scan);
            }
        });
    }

    private void askCreateItem(ScanResult scan) {
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
                        .setPositiveButton(R.string.treffer_anlegen, (dialog, which) ->
                                startActivity(ItemEditActivity.newIntent(this, ziel.id, scan)))
                        .show();
                return;
            }
            showLagerAuswahl(lagerListe, scan);
        });
    }

    private void showLagerAuswahl(List<Lager> lagerListe, ScanResult scan) {
        String[] namen = new String[lagerListe.size()];
        for (int i = 0; i < lagerListe.size(); i++) {
            namen[i] = lagerListe.get(i).name;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lager_auswaehlen)
                .setItems(namen, (dialog, which) ->
                        startActivity(ItemEditActivity.newIntent(this, lagerListe.get(which).id, scan)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private String value(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    @Nullable
    private String nullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
