package de.tagstock.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.Image;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Kamera- und NFC-Erkennung fuer Bildschirme, die scannen. Kapselt CameraX,
 * ML Kit und den NFC-Reader-Mode, damit Activity und Fragment dieselbe Logik
 * benutzen koennen.
 */
public class ScannerSteuerung {

    public interface Listener {
        /** Wird auf dem Main-Thread mit jedem erkannten Code aufgerufen. */
        void onCode(ScanResult ergebnis);

        /** Kamera steht nicht zur Verfuegung; NFC bleibt nutzbar. */
        default void onKameraFehlt() {
        }

        /** Kamera laeuft - z. B. um den Blitzschalter einzublenden. */
        default void onKameraBereit() {
        }
    }

    /** Derselbe Code wird innerhalb dieser Zeitspanne nur einmal gemeldet. */
    private static final long ENTPRELLZEIT_MS = 2000L;

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView preview;
    private final Listener listener;

    private ExecutorService analyse;
    private BarcodeScanner barcodeScanner;
    private Camera camera;
    private boolean blitzAn;
    private boolean angehalten;

    private String letzterCode;
    private long letzterZeitpunkt;

    public ScannerSteuerung(Context context, LifecycleOwner lifecycleOwner,
                            PreviewView preview, Listener listener) {
        this.context = context.getApplicationContext();
        this.lifecycleOwner = lifecycleOwner;
        this.preview = preview;
        this.listener = listener;
    }

    /** Startet die Kamera. Die Berechtigung muss vorher erteilt sein. */
    public void kameraStarten() {
        if (analyse == null) {
            analyse = Executors.newSingleThreadExecutor();
        }
        if (barcodeScanner == null) {
            barcodeScanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build());
        }
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            try {
                binden(future.get());
                listener.onKameraBereit();
            } catch (Exception e) {
                listener.onKameraFehlt();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    /** NFC-Tags lesen, solange der Bildschirm im Vordergrund ist. */
    public void nfcStarten(Activity activity) {
        NfcHelper.enableReader(activity, tag -> {
            ScanResult ergebnis = NfcHelper.read(tag);
            activity.runOnUiThread(() -> melden(ergebnis));
        });
    }

    public void nfcBeenden(Activity activity) {
        NfcHelper.disableReader(activity);
    }

    public void freigeben() {
        if (analyse != null) {
            analyse.shutdown();
            analyse = null;
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
            barcodeScanner = null;
        }
    }

    public boolean hatBlitz() {
        return camera != null && camera.getCameraInfo().hasFlashUnit();
    }

    public boolean blitzUmschalten() {
        if (!hatBlitz()) {
            return false;
        }
        blitzAn = !blitzAn;
        camera.getCameraControl().enableTorch(blitzAn);
        return blitzAn;
    }

    /** Haelt die Auswertung an, z. B. solange ein Dialog offen ist. */
    public void anhalten(boolean anhalten) {
        this.angehalten = anhalten;
    }

    /** Laesst denselben Code sofort wieder zu. */
    public void entprellungZuruecksetzen() {
        letzterCode = null;
        letzterZeitpunkt = 0L;
    }

    // Die Analyse greift auf das Kamerabild zu; die Markierung muss auch hier
    // stehen, weil die Methodenreferenz sie weiterreicht.
    @androidx.annotation.OptIn(markerClass = ExperimentalGetImage.class)
    private void binden(ProcessCameraProvider provider) {
        Preview vorschau = new Preview.Builder().build();
        vorschau.setSurfaceProvider(preview.getSurfaceProvider());

        ResolutionSelector aufloesung = new ResolutionSelector.Builder()
                .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build();
        ImageAnalysis analyseFall = new ImageAnalysis.Builder()
                .setResolutionSelector(aufloesung)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analyseFall.setAnalyzer(analyse, this::analysieren);

        provider.unbindAll();
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                vorschau, analyseFall);
    }

    @ExperimentalGetImage
    @SuppressLint("UnsafeOptInUsageError")
    private void analysieren(@NonNull ImageProxy proxy) {
        Image bild = proxy.getImage();
        if (bild == null || barcodeScanner == null) {
            proxy.close();
            return;
        }
        InputImage eingabe = InputImage.fromMediaImage(bild,
                proxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(eingabe)
                .addOnSuccessListener(this::aufBarcodes)
                .addOnCompleteListener(aufgabe -> proxy.close());
    }

    private void aufBarcodes(List<Barcode> barcodes) {
        for (Barcode barcode : barcodes) {
            String wert = barcode.getRawValue();
            if (wert == null || wert.isEmpty()) {
                continue;
            }
            CodeArt art = barcode.getFormat() == Barcode.FORMAT_QR_CODE
                    ? CodeArt.QR : CodeArt.BARCODE;
            melden(new ScanResult(wert, art, formatName(barcode.getFormat())));
            return;
        }
    }

    private void melden(ScanResult ergebnis) {
        if (angehalten) {
            return;
        }
        long jetzt = System.currentTimeMillis();
        if (ergebnis.code.equals(letzterCode) && jetzt - letzterZeitpunkt < ENTPRELLZEIT_MS) {
            return;
        }
        letzterCode = ergebnis.code;
        letzterZeitpunkt = jetzt;
        listener.onCode(ergebnis);
    }

    @Nullable
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
                return null;
        }
    }
}
