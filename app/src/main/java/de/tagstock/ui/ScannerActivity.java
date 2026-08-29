package de.tagstock.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.tagstock.R;
import de.tagstock.data.CodeType;
import de.tagstock.databinding.ActivityScannerBinding;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;

/**
 * Vollbild-Scanner: liest Barcodes und QR-Codes ueber die Kamera und
 * gleichzeitig NFC-Tags ueber den Reader-Mode.
 */
public class ScannerActivity extends AppCompatActivity {

    private ActivityScannerBinding binding;
    private ExecutorService analysisExecutor;
    private BarcodeScanner barcodeScanner;
    private androidx.camera.core.Camera camera;
    private boolean torchOn;

    /** Verhindert, dass mehrere Treffer hintereinander verarbeitet werden. */
    private final AtomicBoolean handled = new AtomicBoolean(false);

    private final androidx.activity.result.ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    showCameraUnavailable();
                }
            });

    public static Intent createIntent(Context context) {
        return new Intent(context, ScannerActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        analysisExecutor = Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.buttonTorch.setOnClickListener(v -> toggleTorch());
        binding.buttonManuell.setOnClickListener(v -> askManualCode());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }

        updateHint();
    }

    @Override
    protected void onResume() {
        super.onResume();
        NfcHelper.enableReader(this, tag -> {
            ScanResult result = NfcHelper.read(tag);
            runOnUiThread(() -> finishWithResult(result));
        });
    }

    @Override
    protected void onPause() {
        NfcHelper.disableReader(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        super.onDestroy();
    }

    private void updateHint() {
        if (!NfcHelper.hasHardware(this)) {
            return;
        }
        if (!NfcHelper.isReady(this)) {
            binding.textHint.setText(R.string.scan_nfc_aus);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                bindUseCases(provider);
            } catch (Exception e) {
                showCameraUnavailable();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases(ProcessCameraProvider provider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::analyze);

        provider.unbindAll();
        camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
        binding.buttonTorch.setVisibility(
                camera.getCameraInfo().hasFlashUnit() ? View.VISIBLE : View.GONE);
    }

    @SuppressLint("UnsafeOptInUsageError")
    @ExperimentalGetImage
    private void analyze(@NonNull ImageProxy proxy) {
        Image image = proxy.getImage();
        if (image == null || handled.get()) {
            proxy.close();
            return;
        }
        InputImage input = InputImage.fromMediaImage(image, proxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(input)
                .addOnSuccessListener(this::onBarcodes)
                .addOnCompleteListener(task -> proxy.close());
    }

    private void onBarcodes(List<Barcode> barcodes) {
        for (Barcode barcode : barcodes) {
            String value = barcode.getRawValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            CodeType type = barcode.getFormat() == Barcode.FORMAT_QR_CODE
                    ? CodeType.QR : CodeType.BARCODE;
            finishWithResult(new ScanResult(value, type, formatName(barcode.getFormat())));
            return;
        }
    }

    private void askManualCode() {
        de.tagstock.util.Dialogs.textInput(this, getString(R.string.scan_manuell_titel),
                getString(R.string.artikel_code), null,
                value -> finishWithResult(new ScanResult(value, CodeType.MANUELL, null)));
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            return;
        }
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
    }

    private void showCameraUnavailable() {
        binding.previewView.setVisibility(View.GONE);
        binding.scanFrame.setVisibility(View.GONE);
        binding.buttonTorch.setVisibility(View.GONE);
        binding.textNoCamera.setVisibility(View.VISIBLE);
        binding.textHint.setText(R.string.scan_hinweis_nur_nfc);
    }

    private void finishWithResult(ScanResult result) {
        if (!handled.compareAndSet(false, true)) {
            return;
        }
        Toast.makeText(this, getString(R.string.scan_erkannt, result.code), Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_OK, result.toIntent());
        finish();
    }

    private String formatName(int format) {
        switch (format) {
            case Barcode.FORMAT_QR_CODE:
                return "QR";
            case Barcode.FORMAT_EAN_13:
                return "EAN-13";
            case Barcode.FORMAT_EAN_8:
                return "EAN-8";
            case Barcode.FORMAT_CODE_128:
                return "Code 128";
            case Barcode.FORMAT_CODE_39:
                return "Code 39";
            case Barcode.FORMAT_UPC_A:
                return "UPC-A";
            case Barcode.FORMAT_UPC_E:
                return "UPC-E";
            case Barcode.FORMAT_DATA_MATRIX:
                return "Data Matrix";
            case Barcode.FORMAT_ITF:
                return "ITF";
            case Barcode.FORMAT_PDF417:
                return "PDF417";
            case Barcode.FORMAT_AZTEC:
                return "Aztec";
            default:
                return getString(R.string.format_unbekannt);
        }
    }
}
