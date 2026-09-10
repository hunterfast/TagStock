package de.tagstock.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;

import de.tagstock.BuildConfig;
import de.tagstock.R;
import de.tagstock.data.Bestand;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityEinstellungenBinding;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.Appfassungen;
import de.tagstock.util.ServerClient;
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

        binding.textVersion.setText(getString(R.string.einstellungen_app_version,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        binding.buttonNachUpdateSehen.setOnClickListener(v -> nachUpdateSehen(true));
        nachUpdateSehen(false);
    }

    // ------------------------------------------------------------ Aktualisierung

    /**
     * Fragt den Server, ob dort eine neuere App liegt und ob er selbst noch
     * aktuell ist. Ohne Server bleibt der Abschnitt still.
     */
    private void nachUpdateSehen(boolean melden) {
        String url = Einstellungen.serverUrl(this);
        String token = Einstellungen.token(this);
        if (url == null || token == null) {
            if (melden) {
                Toast.makeText(this, R.string.server_nicht_eingerichtet,
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        binding.buttonNachUpdateSehen.setEnabled(false);
        Hintergrund.starte(() -> {
            ServerClient client = new ServerClient(url, token);
            JSONObject[] antworten = new JSONObject[2];
            antworten[0] = client.appAuskunft();
            try {
                antworten[1] = client.aktualisierung();
            } catch (Exception ohneAntwort) {
                // Aeltere Server kennen die Auskunft noch nicht - kein Grund zur Sorge.
                antworten[1] = null;
            }
            return antworten;
        }, antworten -> {
            binding.buttonNachUpdateSehen.setEnabled(true);
            String wurzel = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            appstandZeigen(antworten[0], wurzel, melden);
            serverstandZeigen(antworten[1]);
        }, fehler -> {
            binding.buttonNachUpdateSehen.setEnabled(true);
            if (melden) {
                String meldung = fehler.getMessage();
                Toast.makeText(this, meldung == null
                                ? getString(R.string.server_nicht_erreichbar) : meldung,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void appstandZeigen(JSONObject app, String url, boolean melden) {
        JSONObject aktuell = app == null ? null : app.optJSONObject("aktuell");
        JSONObject vorher = app == null ? null : app.optJSONObject("vorher");

        boolean neuer = aktuell != null
                && aktuell.optInt("versionCode") > BuildConfig.VERSION_CODE;
        binding.buttonAppLaden.setVisibility(neuer ? View.VISIBLE : View.GONE);
        if (neuer) {
            String name = aktuell.optString("versionName",
                    String.valueOf(aktuell.optInt("versionCode")));
            binding.buttonAppLaden.setText(getString(R.string.einstellungen_app_neu, name));
            binding.buttonAppLaden.setOnClickListener(v -> aktualisierenFragen(url, aktuell));
        }

        // Zurueck geht nur auf eine Fassung, die aelter ist als die laufende.
        boolean rueckweg = vorher != null && vorher.optInt("versionCode") > 0
                && vorher.optInt("versionCode") < BuildConfig.VERSION_CODE;
        binding.buttonAppZurueck.setVisibility(rueckweg ? View.VISIBLE : View.GONE);
        if (rueckweg) {
            String name = vorher.optString("versionName",
                    String.valueOf(vorher.optInt("versionCode")));
            binding.buttonAppZurueck.setText(
                    getString(R.string.einstellungen_app_zurueck, name));
            binding.buttonAppZurueck.setOnClickListener(v -> zurueckFragen(url, vorher, name));
        }

        if (!melden || neuer) {
            return;
        }
        Toast.makeText(this, aktuell != null
                        ? R.string.einstellungen_app_aktuell : R.string.einstellungen_app_keine,
                Toast.LENGTH_SHORT).show();
    }

    /** Nachfragen, sichern, laden - und dann dem System uebergeben. */
    private void aktualisierenFragen(String url, JSONObject fassung) {
        String name = fassung.optString("versionName",
                String.valueOf(fassung.optInt("versionCode")));
        String hinweis = fassung.optString("hinweis", "");
        StringBuilder text = new StringBuilder(getString(R.string.update_text));
        if (!hinweis.isEmpty()) {
            text.append("\n\n").append(hinweis);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.update_titel, name))
                .setMessage(text.toString())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.update_installieren, (dialog, welcher) ->
                        aktualisieren(url, fassung))
                .show();
    }

    private void aktualisieren(String url, JSONObject fassung) {
        int version = fassung.optInt("versionCode");
        String adresse = url + fassung.optString("adresse", "/api/v1/app/aktuell/tagstock.apk");
        Toast.makeText(this, R.string.update_laedt, Toast.LENGTH_SHORT).show();
        binding.buttonAppLaden.setEnabled(false);

        repository.ladeBestand(bestand -> Hintergrund.starte(() -> {
            // Erst sichern, dann laden: wer zurueckmuss, hat die Daten von vorher.
            File sicherung = sicherungSchreiben(bestand);
            File apk = Appfassungen.herunterladen(this, adresse, version);
            return new Object[]{sicherung, apk};
        }, ergebnis -> {
            binding.buttonAppLaden.setEnabled(true);
            File sicherung = (File) ergebnis[0];
            if (sicherung != null) {
                Toast.makeText(this, getString(R.string.update_sicherung,
                        sicherung.getAbsolutePath()), Toast.LENGTH_LONG).show();
            }
            Intent installieren = Appfassungen.installieren(this, (File) ergebnis[1]);
            if (installieren == null) {
                Toast.makeText(this, R.string.update_kein_installer, Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(installieren);
        }, fehler -> {
            binding.buttonAppLaden.setEnabled(true);
            String meldung = fehler.getMessage();
            Toast.makeText(this, meldung == null
                    ? getString(R.string.server_nicht_erreichbar) : meldung,
                    Toast.LENGTH_LONG).show();
        }));
    }

    /**
     * Der Weg zurueck geht nur ueber Deinstallieren - Android laesst eine
     * aeltere Fassung nicht ueber eine neuere. Die Datei kommt deshalb in den
     * Download-Ordner, wo sie das Deinstallieren ueberlebt.
     */
    private void zurueckFragen(String url, JSONObject fassung, String name) {
        String adresse = url + fassung.optString("adresse", "/api/v1/app/vorher/tagstock.apk");
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.zurueck_titel, name))
                .setMessage(R.string.zurueck_text)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.zurueck_laden, (dialog, welcher) ->
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(adresse))))
                .show();
    }

    /** Bestand als JSON neben die App legen; der Pfad steht danach in der Meldung. */
    private File sicherungSchreiben(Bestand bestand) throws Exception {
        File ordner = getExternalFilesDir("sicherungen");
        if (ordner == null) {
            ordner = new File(getFilesDir(), "sicherungen");
        }
        //noinspection ResultOfMethodCallIgnored
        ordner.mkdirs();
        File ziel = new File(ordner, Sicherung.dateiname("json"));
        try (java.io.OutputStream aus = new java.io.FileOutputStream(ziel)) {
            aus.write(Sicherung.alsJson(bestand).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return ziel;
    }

    private void serverstandZeigen(@Nullable JSONObject stand) {
        if (stand == null) {
            binding.textVersionServer.setVisibility(View.GONE);
            return;
        }
        String version = stand.optString("version", "");
        String gebaut = stand.optString("gebautAm", "");
        StringBuilder text = new StringBuilder();
        if (!version.isEmpty()) {
            text.append(getString(R.string.einstellungen_server_version, version,
                    gebaut.length() >= 10 ? gebaut.substring(0, 10) : gebaut));
        }
        if (stand.optBoolean("aktualisierungVerfuegbar")) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(getString(R.string.einstellungen_server_neuer));
        }
        binding.textVersionServer.setVisibility(text.length() == 0 ? View.GONE : View.VISIBLE);
        binding.textVersionServer.setText(text.toString());
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
