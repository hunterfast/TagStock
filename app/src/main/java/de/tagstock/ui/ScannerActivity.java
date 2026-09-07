package de.tagstock.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;

import de.tagstock.R;
import de.tagstock.databinding.ActivityScannerBinding;
import de.tagstock.util.CodeArt;
import de.tagstock.util.Dialogs;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;
import de.tagstock.util.ScannerSteuerung;

/** Vollbild-Scanner fuer einen einzelnen Code; liefert das Ergebnis zurueck. */
public class ScannerActivity extends AppCompatActivity implements ScannerSteuerung.Listener {

    private ActivityScannerBinding binding;
    private ScannerSteuerung scanner;
    private final AtomicBoolean erledigt = new AtomicBoolean(false);

    private final ActivityResultLauncher<String> berechtigung = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), erlaubt -> {
                if (erlaubt) {
                    scanner.kameraStarten();
                } else {
                    onKameraFehlt();
                }
            });

    public static Intent intent(Context context) {
        return new Intent(context, ScannerActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scanner = new ScannerSteuerung(this, this, binding.previewView, this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.buttonBlitz.setVisibility(View.GONE);
        binding.buttonBlitz.setOnClickListener(v -> scanner.blitzUmschalten());
        binding.buttonManuell.setOnClickListener(v -> Dialogs.textInput(this,
                getString(R.string.scan_manuell_titel), getString(R.string.artikel_kennung), null,
                wert -> onCode(new ScanResult(wert, CodeArt.MANUELL, null))));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            scanner.kameraStarten();
        } else {
            berechtigung.launch(Manifest.permission.CAMERA);
        }

        if (NfcHelper.hasHardware(this) && !NfcHelper.isReady(this)) {
            binding.textHinweis.setText(R.string.scan_nfc_aus);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanner.nfcStarten(this);
    }

    @Override
    protected void onPause() {
        scanner.nfcBeenden(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scanner.freigeben();
        super.onDestroy();
    }

    @Override
    public void onCode(ScanResult ergebnis) {
        if (!erledigt.compareAndSet(false, true)) {
            return;
        }
        Toast.makeText(this, getString(R.string.scan_erkannt, ergebnis.code),
                Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_OK, ergebnis.toIntent());
        finish();
    }

    @Override
    public void onKameraBereit() {
        binding.buttonBlitz.setVisibility(scanner.hatBlitz() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onKameraFehlt() {
        binding.previewView.setVisibility(View.GONE);
        binding.scanRahmen.setVisibility(View.GONE);
        binding.buttonBlitz.setVisibility(View.GONE);
        binding.textKeineKamera.setVisibility(View.VISIBLE);
        binding.textHinweis.setText(R.string.scan_hinweis_nur_nfc);
    }
}
