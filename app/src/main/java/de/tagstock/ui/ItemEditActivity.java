package de.tagstock.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.tagstock.R;
import de.tagstock.data.Code;
import de.tagstock.data.CodeType;
import de.tagstock.data.Item;
import de.tagstock.data.ItemWithState;
import de.tagstock.data.Lager;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityItemEditBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.FotoLader;
import de.tagstock.util.Fotos;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;

/** Anlegen und Bearbeiten eines Artikels samt Codes, Foto und Ausleihen. */
public class ItemEditActivity extends AppCompatActivity
        implements CodeAdapter.Listener, VerleihAdapter.Listener {

    private static final String EXTRA_ITEM_ID = "de.tagstock.extra.ITEM_ID";
    private static final String EXTRA_LAGER_ID = "de.tagstock.extra.LAGER_ID";

    private ActivityItemEditBinding binding;
    private Repository repository;

    private long itemId;
    private Item item;
    private ItemWithState zustand;

    private final List<Lager> lagerListe = new ArrayList<>();
    private long ausgewaehltesLager;

    private CodeAdapter codeAdapter;
    private VerleihAdapter verleihAdapter;

    /** Codes, die vor dem ersten Speichern erfasst wurden. */
    private final List<Code> offeneCodes = new ArrayList<>();
    private long naechsteTempId = -1L;

    private String fotoPfad;
    private String aufnahmeName;
    private final List<String> neueFotos = new ArrayList<>();
    private AlertDialog nfcDialog;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    ScanResult scan = ScanResult.fromIntent(result.getData());
                    if (scan != null) {
                        codeErfassen(scan.code, scan.codeType);
                    }
                }
            });

    private final ActivityResultLauncher<Uri> kameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), erfolg -> {
                if (erfolg != null && erfolg && aufnahmeName != null) {
                    fotoUebernehmen(aufnahmeName);
                } else {
                    Fotos.loeschen(this, aufnahmeName);
                }
                aufnahmeName = null;
            });

    private final ActivityResultLauncher<String> galerieLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                String name = Fotos.uebernehmen(this, uri);
                if (name == null) {
                    Toast.makeText(this, R.string.foto_fehler, Toast.LENGTH_SHORT).show();
                } else {
                    fotoUebernehmen(name);
                }
            });

    /** Bestehenden Artikel oeffnen. */
    public static Intent editIntent(Context context, long itemId) {
        Intent intent = new Intent(context, ItemEditActivity.class);
        intent.putExtra(EXTRA_ITEM_ID, itemId);
        return intent;
    }

    /** Neuen Artikel anlegen, optional mit bereits gescanntem Code. */
    public static Intent newIntent(Context context, long lagerId, @Nullable ScanResult scan) {
        Intent intent = new Intent(context, ItemEditActivity.class);
        intent.putExtra(EXTRA_LAGER_ID, lagerId);
        if (scan != null) {
            intent.putExtra(ScanResult.EXTRA_CODE, scan.code);
            intent.putExtra(ScanResult.EXTRA_CODE_TYPE, scan.codeType.name());
        }
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityItemEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = Repository.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> zurueck());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                zurueck();
            }
        });

        itemId = getIntent().getLongExtra(EXTRA_ITEM_ID, 0L);
        ausgewaehltesLager = getIntent().getLongExtra(EXTRA_LAGER_ID, 0L);
        setTitle(itemId == 0 ? R.string.artikel_neu : R.string.artikel_bearbeiten);

        codeAdapter = new CodeAdapter(this);
        binding.recyclerCodes.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerCodes.setAdapter(codeAdapter);
        binding.recyclerCodes.setNestedScrollingEnabled(false);

        verleihAdapter = new VerleihAdapter(this);
        binding.recyclerVerleih.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerVerleih.setAdapter(verleihAdapter);
        binding.recyclerVerleih.setNestedScrollingEnabled(false);

        binding.buttonCodeScannen.setOnClickListener(
                v -> scanLauncher.launch(ScannerActivity.createIntent(this)));
        binding.buttonCodeEingeben.setOnClickListener(v -> Dialogs.textInput(this,
                getString(R.string.scan_manuell_titel), getString(R.string.artikel_code), null,
                wert -> codeErfassen(wert, CodeType.MANUELL)));
        binding.buttonNfcSchreiben.setOnClickListener(v -> nfcSchreibenStarten());

        binding.buttonFotoKamera.setOnClickListener(v -> fotoAufnehmen());
        binding.buttonFotoGalerie.setOnClickListener(v -> galerieLauncher.launch("image/*"));
        binding.buttonFotoEntfernen.setOnClickListener(v -> fotoEntfernen());

        binding.buttonAusleihen.setOnClickListener(v -> {
            if (zustand != null) {
                ItemAktionen.ausleihen(this, repository, zustand, null);
            }
        });
        binding.buttonZurueck.setOnClickListener(
                v -> ItemAktionen.zurueckgeben(this, repository, itemId, null));
        binding.buttonVerloren.setOnClickListener(v -> {
            if (zustand != null) {
                ItemAktionen.verlorenMelden(this, repository, zustand, null);
            }
        });
        binding.buttonGefunden.setOnClickListener(v -> {
            if (zustand != null) {
                ItemAktionen.wiedergefunden(this, repository, zustand, null);
            }
        });

        binding.buttonSpeichern.setOnClickListener(v -> speichern());

        lagerLaden();

        String codeAusIntent = getIntent().getStringExtra(ScanResult.EXTRA_CODE);
        if (itemId == 0) {
            binding.editMenge.setText("1");
            binding.gruppeBestand.setVisibility(View.GONE);
            binding.gruppeVerleih.setVisibility(View.GONE);
            binding.buttonNfcSchreiben.setVisibility(View.GONE);
            if (codeAusIntent != null) {
                codeErfassen(codeAusIntent,
                        CodeType.fromName(getIntent().getStringExtra(ScanResult.EXTRA_CODE_TYPE)));
            }
        } else {
            beobachten();
        }
    }

    /** Bestehender Artikel: Zustand, Codes und Ausleihen live verfolgen. */
    private void beobachten() {
        repository.observeItemState(itemId).observe(this, geladen -> {
            if (geladen == null) {
                finish();
                return;
            }
            boolean erstesMal = item == null;
            zustand = geladen;
            item = geladen.item;
            if (erstesMal) {
                felderFuellen(geladen.item);
            }
            bestandAnzeigen(geladen);
        });
        repository.observeCodes(itemId).observe(this, codes -> {
            codeAdapter.submitList(codes);
            binding.textKeineCodes.setVisibility(
                    codes == null || codes.isEmpty() ? View.VISIBLE : View.GONE);
        });
        repository.observeVerleih(itemId).observe(this, verleihe -> {
            verleihAdapter.submitList(verleihe);
            binding.textKeinVerleih.setVisibility(
                    verleihe == null || verleihe.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_item_edit, menu);
        menu.findItem(R.id.action_delete_item).setVisible(itemId != 0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.action_delete_item && item != null) {
            Dialogs.confirm(this, getString(R.string.artikel_loeschen_titel),
                    getString(R.string.artikel_loeschen_text, item.name),
                    R.string.action_delete, () -> {
                        Fotos.loeschen(this, item.fotoPfad);
                        repository.deleteItem(item);
                        Toast.makeText(this, R.string.artikel_geloescht, Toast.LENGTH_SHORT).show();
                        finish();
                    });
            return true;
        }
        if (menuItem.getItemId() == android.R.id.home) {
            zurueck();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    // ------------------------------------------------------------------ Felder

    private void lagerLaden() {
        repository.loadAllLager(liste -> {
            lagerListe.clear();
            lagerListe.addAll(liste);
            List<String> namen = new ArrayList<>();
            for (Lager lager : liste) {
                namen.add(lager.name);
            }
            binding.dropdownLager.setAdapter(new ArrayAdapter<>(
                    this, android.R.layout.simple_list_item_1, namen));
            binding.dropdownLager.setOnItemClickListener((parent, view, position, id) ->
                    ausgewaehltesLager = lagerListe.get(position).id);
            lagerAuswahlAnzeigen();
        });
    }

    private void lagerAuswahlAnzeigen() {
        for (Lager lager : lagerListe) {
            if (lager.id == ausgewaehltesLager) {
                binding.dropdownLager.setText(lager.name, false);
                return;
            }
        }
        if (!lagerListe.isEmpty()) {
            ausgewaehltesLager = lagerListe.get(0).id;
            binding.dropdownLager.setText(lagerListe.get(0).name, false);
        }
    }

    private void felderFuellen(Item geladen) {
        binding.editName.setText(geladen.name);
        binding.editBeschreibung.setText(geladen.beschreibung);
        binding.editMenge.setText(String.valueOf(geladen.menge));
        binding.editNotiz.setText(geladen.notiz);
        ausgewaehltesLager = geladen.lagerId;
        fotoPfad = geladen.fotoPfad;
        lagerAuswahlAnzeigen();
        fotoAnzeigen();
    }

    private void bestandAnzeigen(ItemWithState state) {
        binding.textBestand.setText(de.tagstock.util.Formatter.bestandText(this, state));
        binding.buttonAusleihen.setEnabled(state.vorhanden() > 0);
        binding.buttonZurueck.setEnabled(state.verliehen > 0);
        binding.buttonVerloren.setEnabled(state.vorhanden() > 0);
        binding.buttonGefunden.setEnabled(state.verloren() > 0);
    }

    // -------------------------------------------------------------------- Foto

    private void fotoAufnehmen() {
        try {
            aufnahmeName = Fotos.neuerName();
            kameraLauncher.launch(Fotos.uriFuer(this, aufnahmeName));
        } catch (IOException e) {
            aufnahmeName = null;
            Toast.makeText(this, R.string.foto_fehler, Toast.LENGTH_SHORT).show();
        }
    }

    private void fotoUebernehmen(String name) {
        neueFotos.add(name);
        fotoPfad = name;
        FotoLader.vergessen(name);
        fotoAnzeigen();
    }

    private void fotoEntfernen() {
        fotoPfad = null;
        fotoAnzeigen();
    }

    private void fotoAnzeigen() {
        boolean vorhanden = Fotos.existiert(this, fotoPfad);
        FotoLader.laden(binding.imageFoto, vorhanden ? fotoPfad : null, R.drawable.ic_lager);
        binding.buttonFotoEntfernen.setVisibility(vorhanden ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------- Codes

    /** Haengt einen Code an - bei bestehenden Artikeln sofort, sonst gemerkt. */
    private void codeErfassen(String wert, CodeType typ) {
        if (wert == null || wert.trim().isEmpty()) {
            return;
        }
        String bereinigt = wert.trim();
        if (itemId == 0) {
            for (Code offen : offeneCodes) {
                if (offen.wert.equals(bereinigt)) {
                    return;
                }
            }
            Code code = new Code(0L, bereinigt, typ);
            code.id = naechsteTempId--;
            offeneCodes.add(code);
            codeAdapter.submitList(new ArrayList<>(offeneCodes));
            binding.textKeineCodes.setVisibility(View.GONE);
            return;
        }
        repository.codeHinzufuegen(itemId, bereinigt, typ, ergebnis -> {
            if (ergebnis.erfolgreich) {
                Toast.makeText(this, R.string.code_hinzugefuegt, Toast.LENGTH_SHORT).show();
            } else {
                codeBelegtMelden(bereinigt, ergebnis.belegtVon);
            }
        });
    }

    private void codeBelegtMelden(String wert, @Nullable Repository.Treffer belegtVon) {
        if (belegtVon == null) {
            Toast.makeText(this, R.string.code_belegt_unbekannt, Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.code_belegt_titel)
                .setMessage(getString(R.string.code_belegt_text, wert, belegtVon.item.item.name))
                .setNegativeButton(R.string.action_ok, null)
                .setPositiveButton(R.string.treffer_oeffnen, (dialog, welcher) ->
                        startActivity(editIntent(this, belegtVon.item.item.id)))
                .show();
    }

    @Override
    public void onCodeEntfernen(Code code) {
        if (itemId == 0) {
            offeneCodes.remove(code);
            codeAdapter.submitList(new ArrayList<>(offeneCodes));
            binding.textKeineCodes.setVisibility(offeneCodes.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        repository.codeEntfernen(code);
    }

    // --------------------------------------------------------------------- NFC

    /** Schreibt Artikelname und Nummer als Text auf ein NFC-Tag. */
    private void nfcSchreibenStarten() {
        if (item == null) {
            return;
        }
        if (!NfcHelper.isReady(this)) {
            Toast.makeText(this, R.string.nfc_nicht_bereit, Toast.LENGTH_LONG).show();
            return;
        }
        nfcDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nfc_schreiben_titel)
                .setMessage(R.string.nfc_schreiben_text)
                .setNegativeButton(R.string.action_cancel, (dialog, welcher) -> nfcBeenden())
                .setOnDismissListener(dialog -> NfcHelper.disableReader(this))
                .show();

        String inhalt = "TagStock #" + item.id + " " + item.name;
        NfcHelper.enableReader(this, tag -> {
            NfcHelper.Schreibergebnis ergebnis = NfcHelper.schreibe(tag, inhalt);
            String uid = NfcHelper.toHex(tag.getId());
            runOnUiThread(() -> {
                Toast.makeText(this, ergebnis.meldungRes, Toast.LENGTH_LONG).show();
                if (ergebnis == NfcHelper.Schreibergebnis.OK) {
                    // Die UID als Code hinterlegen, damit das Tag auch dann
                    // gefunden wird, wenn der Text spaeter ueberschrieben wird.
                    codeErfassen(uid, CodeType.NFC);
                }
                nfcBeenden();
            });
        });
    }

    private void nfcBeenden() {
        NfcHelper.disableReader(this);
        if (nfcDialog != null && nfcDialog.isShowing()) {
            nfcDialog.dismiss();
        }
        nfcDialog = null;
    }

    @Override
    protected void onPause() {
        if (nfcDialog != null) {
            nfcBeenden();
        }
        super.onPause();
    }

    // ----------------------------------------------------------------- Verleih

    @Override
    public void onZurueckgeben(de.tagstock.data.Verleih verleih) {
        ItemAktionen.zurueckBuchen(this, repository, verleih, null);
    }

    @Override
    public void onEintragLoeschen(de.tagstock.data.Verleih verleih) {
        Dialogs.confirm(this, getString(R.string.verleih_eintrag_loeschen_titel),
                getString(R.string.verleih_eintrag_loeschen_text, verleih.person),
                R.string.action_delete, () -> repository.verleihLoeschen(verleih));
    }

    // ------------------------------------------------------------------ Sichern

    private void speichern() {
        String name = text(binding.editName.getText());
        if (name.isEmpty()) {
            binding.editName.setError(getString(R.string.artikel_name_fehlt));
            binding.editName.requestFocus();
            return;
        }
        if (ausgewaehltesLager == 0) {
            Toast.makeText(this, R.string.treffer_kein_lager, Toast.LENGTH_LONG).show();
            return;
        }

        Item ziel = item == null ? new Item() : item.copy();
        ziel.name = name;
        ziel.lagerId = ausgewaehltesLager;
        ziel.beschreibung = leerZuNull(text(binding.editBeschreibung.getText()));
        ziel.notiz = leerZuNull(text(binding.editNotiz.getText()));
        ziel.menge = Math.max(0, zahl(text(binding.editMenge.getText()), 1));
        ziel.fotoPfad = fotoPfad;

        // Verliehene und verlorene Stuecke koennen nicht mehr sein als der Bestand.
        if (zustand != null) {
            int gebunden = zustand.verliehen + ziel.mengeVerloren;
            if (ziel.menge < gebunden) {
                Toast.makeText(this, getString(R.string.artikel_menge_zu_klein, gebunden),
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (item != null && item.fotoPfad != null && !item.fotoPfad.equals(fotoPfad)) {
            Fotos.loeschen(this, item.fotoPfad);
        }

        if (item == null) {
            repository.insertItem(ziel, neueId -> offeneCodesSpeichern(neueId));
        } else {
            repository.updateItem(ziel, () -> {
                Toast.makeText(this, R.string.artikel_gespeichert, Toast.LENGTH_SHORT).show();
                fertig();
            });
        }
    }

    /** Nach dem Anlegen die gemerkten Codes nachziehen. */
    private void offeneCodesSpeichern(long neueId) {
        if (offeneCodes.isEmpty()) {
            Toast.makeText(this, R.string.artikel_gespeichert, Toast.LENGTH_SHORT).show();
            fertig();
            return;
        }
        final int[] offen = {offeneCodes.size()};
        final List<String> belegt = new ArrayList<>();
        for (Code code : offeneCodes) {
            repository.codeHinzufuegen(neueId, code.wert, code.typ, ergebnis -> {
                if (!ergebnis.erfolgreich) {
                    belegt.add(code.wert);
                }
                if (--offen[0] > 0) {
                    return;
                }
                if (belegt.isEmpty()) {
                    Toast.makeText(this, R.string.artikel_gespeichert, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.code_belegt_uebersprungen,
                            belegt.size()), Toast.LENGTH_LONG).show();
                }
                fertig();
            });
        }
    }

    private void fertig() {
        neueFotos.remove(fotoPfad);
        for (String verwaist : neueFotos) {
            Fotos.loeschen(this, verwaist);
        }
        finish();
    }

    /** Zurueck mit Rueckfrage, wenn Eingaben verloren gingen. */
    private void zurueck() {
        if (!hatAenderungen()) {
            verwerfen();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.aenderungen_titel)
                .setMessage(R.string.aenderungen_text)
                .setNeutralButton(R.string.action_cancel, null)
                .setNegativeButton(R.string.aenderungen_verwerfen, (dialog, welcher) -> verwerfen())
                .setPositiveButton(R.string.action_save, (dialog, welcher) -> speichern())
                .show();
    }

    private void verwerfen() {
        for (String verwaist : neueFotos) {
            if (item == null || !verwaist.equals(item.fotoPfad)) {
                Fotos.loeschen(this, verwaist);
            }
        }
        finish();
    }

    private boolean hatAenderungen() {
        String name = text(binding.editName.getText());
        String beschreibung = text(binding.editBeschreibung.getText());
        String notiz = text(binding.editNotiz.getText());
        int menge = zahl(text(binding.editMenge.getText()), 1);

        if (item == null) {
            return !name.isEmpty() || !beschreibung.isEmpty() || !notiz.isEmpty()
                    || !offeneCodes.isEmpty() || fotoPfad != null;
        }
        return !name.equals(item.name)
                || !beschreibung.equals(text(item.beschreibung))
                || !notiz.equals(text(item.notiz))
                || menge != item.menge
                || ausgewaehltesLager != item.lagerId
                || !text(fotoPfad).equals(text(item.fotoPfad));
    }

    private String text(CharSequence wert) {
        return wert == null ? "" : wert.toString().trim();
    }

    @Nullable
    private String leerZuNull(String wert) {
        return wert == null || wert.isEmpty() ? null : wert;
    }

    private int zahl(String wert, int ersatz) {
        try {
            return Integer.parseInt(wert);
        } catch (NumberFormatException e) {
            return ersatz;
        }
    }
}
