package de.tagstock.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.tagstock.R;
import de.tagstock.data.Bestand;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityEinstellungenBinding;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.Sicherung;

/** Design, Name fuer das Protokoll, Kategorien und Sicherung. */
public class EinstellungenActivity extends AppCompatActivity {

    private ActivityEinstellungenBinding binding;
    private Repository repository;

    private final ActivityResultLauncher<String> jsonExport = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            ziel -> exportieren(ziel, true));

    private final ActivityResultLauncher<String> csvExport = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"),
            ziel -> exportieren(ziel, false));

    private final ActivityResultLauncher<String[]> importAuswahl = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importieren);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Einstellungen.anwenden(this);
        super.onCreate(savedInstanceState);
        binding = ActivityEinstellungenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = Repository.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setTitle(R.string.einstellungen_titel);

        designSetzen(Einstellungen.design(this));
        binding.chipDesignSystem.setOnClickListener(v -> designWaehlen(Einstellungen.DESIGN_SYSTEM));
        binding.chipDesignHell.setOnClickListener(v -> designWaehlen(Einstellungen.DESIGN_HELL));
        binding.chipDesignDunkel.setOnClickListener(v -> designWaehlen(Einstellungen.DESIGN_DUNKEL));

        binding.editNutzer.setText(Einstellungen.nutzer(this));
        binding.buttonNutzerSpeichern.setOnClickListener(v -> {
            String name = binding.editNutzer.getText() == null
                    ? "" : binding.editNutzer.getText().toString().trim();
            Einstellungen.setzeNutzer(this, name);
            Toast.makeText(this, R.string.einstellungen_gespeichert, Toast.LENGTH_SHORT).show();
        });

        binding.buttonKategorien.setOnClickListener(v ->
                startActivity(new Intent(this, KategorienActivity.class)));
        binding.schalterNachschlagen.setChecked(Einstellungen.gtinNachschlagen(this));
        binding.schalterNachschlagen.setOnCheckedChangeListener((knopf, an) ->
                Einstellungen.setzeGtinNachschlagen(this, an));
        binding.buttonServer.setOnClickListener(v ->
                startActivity(new Intent(this, ServerActivity.class)));

        binding.buttonExportJson.setOnClickListener(v ->
                jsonExport.launch(Sicherung.dateiname("json")));
        binding.buttonExportCsv.setOnClickListener(v ->
                csvExport.launch(Sicherung.dateiname("csv")));
        binding.buttonImport.setOnClickListener(v ->
                importAuswahl.launch(new String[]{"application/json", "text/plain", "*/*"}));
    }

    private void designWaehlen(String design) {
        Einstellungen.setzeDesign(this, design);
        designSetzen(design);
        recreate();
    }

    private void designSetzen(String design) {
        binding.chipDesignSystem.setChecked(Einstellungen.DESIGN_SYSTEM.equals(design));
        binding.chipDesignHell.setChecked(Einstellungen.DESIGN_HELL.equals(design));
        binding.chipDesignDunkel.setChecked(Einstellungen.DESIGN_DUNKEL.equals(design));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --------------------------------------------------------------- Sicherung

    private void exportieren(@Nullable Uri ziel, boolean alsJson) {
        if (ziel == null) {
            return;
        }
        repository.ladeBestand(bestand -> Hintergrund.starte(
                () -> {
                    String inhalt = alsJson
                            ? Sicherung.alsJson(bestand) : Sicherung.alsCsv(this, bestand);
                    Sicherung.schreibe(this, ziel, inhalt);
                    return bestand.artikel.size();
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
                .setMessage(getString(R.string.import_frage, bestand.artikel.size()))
                .setNeutralButton(R.string.action_cancel, null)
                .setNegativeButton(R.string.import_ergaenzen, (dialog, welcher) ->
                        repository.bestandErgaenzen(bestand, anzahl -> Toast.makeText(this,
                                getString(R.string.import_ergaenzt, anzahl),
                                Toast.LENGTH_LONG).show()))
                .setPositiveButton(R.string.import_ersetzen, (dialog, welcher) ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.import_ersetzen_titel)
                                .setMessage(R.string.import_ersetzen_text)
                                .setNegativeButton(R.string.action_cancel, null)
                                .setPositiveButton(R.string.import_ersetzen, (d, w) ->
                                        repository.bestandErsetzen(bestand, anzahl ->
                                                Toast.makeText(this,
                                                        getString(R.string.import_fertig, anzahl),
                                                        Toast.LENGTH_LONG).show()))
                                .show())
                .show();
    }
}
