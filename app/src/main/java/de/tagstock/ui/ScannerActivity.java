package de.tagstock.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import de.tagstock.R;
import de.tagstock.data.CodeType;
import de.tagstock.databinding.ActivityScannerBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;

/**
 * Vollbild-Scanner fuer einen einzelnen Code. Liest Barcodes und QR-Codes ueber
 * die Kamera und gleichzeitig NFC-Tags ueber den Reader-Mode.
 */
public class ScannerActivity extends ScannerBaseActivity {

    private ActivityScannerBinding binding;
    private final AtomicBoolean erledigt = new AtomicBoolean(false);

    public static Intent createIntent(Context context) {
        return new Intent(context, ScannerActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.buttonTorch.setOnClickListener(v -> blitzUmschalten());
        binding.buttonManuell.setOnClickListener(v -> manuellEingeben());
        binding.buttonTorch.setVisibility(View.GONE);

        scannerStarten(binding.previewView);
        hinweisAktualisieren();
    }

    @Override
    protected void onKameraBereit() {
        binding.buttonTorch.setVisibility(hatBlitz() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onScan(ScanResult ergebnis) {
        if (!erledigt.compareAndSet(false, true)) {
            return;
        }
        Toast.makeText(this, getString(R.string.scan_erkannt, ergebnis.code),
                Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_OK, ergebnis.toIntent());
        finish();
    }

    @Override
    protected void onKameraFehlt() {
        binding.previewView.setVisibility(View.GONE);
        binding.scanFrame.setVisibility(View.GONE);
        binding.buttonTorch.setVisibility(View.GONE);
        binding.textNoCamera.setVisibility(View.VISIBLE);
        binding.textHint.setText(R.string.scan_hinweis_nur_nfc);
    }

    private void hinweisAktualisieren() {
        if (NfcHelper.hasHardware(this) && !NfcHelper.isReady(this)) {
            binding.textHint.setText(R.string.scan_nfc_aus);
        }
    }

    private void manuellEingeben() {
        Dialogs.textInput(this, getString(R.string.scan_manuell_titel),
                getString(R.string.artikel_code), null,
                wert -> onScan(new ScanResult(wert, CodeType.MANUELL, null)));
    }
}
