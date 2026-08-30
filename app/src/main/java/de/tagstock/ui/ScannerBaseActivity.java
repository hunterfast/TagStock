package de.tagstock.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
import android.util.Size;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
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

import de.tagstock.data.CodeType;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;

/**
 * Gemeinsame Grundlage der scannenden Bildschirme: Kameravorschau mit
 * Barcode-Erkennung und parallel aktivem NFC-Reader-Mode.
 */
public abstract class ScannerBaseActivity extends AppCompatActivity {

    /** Derselbe Code wird innerhalb dieser Zeitspanne nur einmal gemeldet. */
    private static final long ENTPRELLZEIT_MS = 2000L;

    private PreviewView preview;
    private ExecutorService analyseExecutor;
    private BarcodeScanner barcodeScanner;
    private Camera camera;
    private boolean blitzAn;

    private String letzterCode;
    private long letzterZeitpunkt;

    private final ActivityResultLauncher<String> berechtigung = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), erlaubt -> {
                if (erlaubt) {
                    kameraBinden();
                } else {
                    onKameraFehlt();
                }
            });

    /** Wird bei jedem erkannten Code aufgerufen - immer auf dem Main-Thread. */
    protected abstract void onScan(ScanResult ergebnis);

    /** Kamera nicht verfuegbar oder abgelehnt; NFC bleibt nutzbar. */
    protected void onKameraFehlt() {
    }

    /** Startet Kamera und Erkennung fuer die uebergebene Vorschau. */
    protected void scannerStarten(PreviewView previewView) {
        preview = previewView;
        analyseExecutor = Executors.newSingleThreadExecutor();
        barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            kameraBinden();
        } else {
            berechtigung.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        NfcHelper.enableReader(this, tag -> {
            ScanResult ergebnis = NfcHelper.read(tag);
            runOnUiThread(() -> melden(ergebnis));
        });
    }

    @Override
    protected void onPause() {
        NfcHelper.disableReader(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (analyseExecutor != null) {
            analyseExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        super.onDestroy();
    }

    protected boolean hatBlitz() {
        return camera != null && camera.getCameraInfo().hasFlashUnit();
    }

    protected boolean blitzUmschalten() {
        if (!hatBlitz()) {
            return false;
        }
        blitzAn = !blitzAn;
        camera.getCameraControl().enableTorch(blitzAn);
        return blitzAn;
    }

    /** Laesst denselben Code sofort wieder zu - etwa nach einem Abbruch. */
    protected void entprellungZuruecksetzen() {
        letzterCode = null;
        letzterZeitpunkt = 0L;
    }

    private void kameraBinden() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                bindeAnwendungsfaelle(future.get());
            } catch (Exception e) {
                onKameraFehlt();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void bindeAnwendungsfaelle(ProcessCameraProvider provider) {
        Preview vorschau = new Preview.Builder().build();
        vorschau.setSurfaceProvider(preview.getSurfaceProvider());

        ResolutionSelector aufloesung = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build();
        ImageAnalysis analyse = new ImageAnalysis.Builder()
                .setResolutionSelector(aufloesung)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analyse.setAnalyzer(analyseExecutor, this::analysieren);

        provider.unbindAll();
        camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, vorschau, analyse);
        onKameraBereit();
    }

    /** Kamera laeuft - Unterklassen koennen z. B. den Blitzschalter einblenden. */
    protected void onKameraBereit() {
    }

    @ExperimentalGetImage
    @SuppressLint("UnsafeOptInUsageError")
    private void analysieren(@NonNull ImageProxy proxy) {
        Image bild = proxy.getImage();
        if (bild == null) {
            proxy.close();
            return;
        }
        InputImage eingabe = InputImage.fromMediaImage(bild, proxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(eingabe)
                .addOnSuccessListener(this::aufBarcodes)
                .addOnCompleteListener(task -> proxy.close());
    }

    private void aufBarcodes(List<Barcode> barcodes) {
        for (Barcode barcode : barcodes) {
            String wert = barcode.getRawValue();
            if (wert == null || wert.isEmpty()) {
                continue;
            }
            CodeType typ = barcode.getFormat() == Barcode.FORMAT_QR_CODE
                    ? CodeType.QR : CodeType.BARCODE;
            melden(new ScanResult(wert, typ, formatName(barcode.getFormat())));
            return;
        }
    }

    /** Entprellt und reicht das Ergebnis an die Unterklasse weiter. */
    private void melden(ScanResult ergebnis) {
        long jetzt = System.currentTimeMillis();
        if (ergebnis.code.equals(letzterCode) && jetzt - letzterZeitpunkt < ENTPRELLZEIT_MS) {
            return;
        }
        letzterCode = ergebnis.code;
        letzterZeitpunkt = jetzt;
        onScan(ergebnis);
    }

    @Nullable
    protected String formatName(int format) {
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
                return null;
        }
    }
}
