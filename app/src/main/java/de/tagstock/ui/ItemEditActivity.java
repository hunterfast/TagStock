package de.tagstock.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import de.tagstock.R;
import de.tagstock.data.CodeType;
import de.tagstock.data.Item;
import de.tagstock.data.ItemStatus;
import de.tagstock.data.Lager;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityItemEditBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Formatter;
import de.tagstock.util.ScanResult;

/** Anlegen und Bearbeiten eines Artikels. */
public class ItemEditActivity extends AppCompatActivity {

    private static final String EXTRA_ITEM_ID = "de.tagstock.extra.ITEM_ID";
    private static final String EXTRA_LAGER_ID = "de.tagstock.extra.LAGER_ID";

    private ActivityItemEditBinding binding;
    private Repository repository;

    private long itemId;
    private Item item;
    private final List<Lager> lagerListe = new ArrayList<>();
    private long selectedLagerId;
    private String code;
    private CodeType codeType = CodeType.KEINER;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    ScanResult scan = ScanResult.fromIntent(result.getData());
                    if (scan != null) {
                        code = scan.code;
                        codeType = scan.codeType;
                        bindCode();
                    }
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
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        itemId = getIntent().getLongExtra(EXTRA_ITEM_ID, 0L);
        selectedLagerId = getIntent().getLongExtra(EXTRA_LAGER_ID, 0L);
        code = getIntent().getStringExtra(ScanResult.EXTRA_CODE);
        codeType = CodeType.fromName(getIntent().getStringExtra(ScanResult.EXTRA_CODE_TYPE));

        setTitle(itemId == 0 ? R.string.artikel_neu : R.string.artikel_bearbeiten);

        binding.buttonScan.setOnClickListener(v -> scanLauncher.launch(ScannerActivity.createIntent(this)));
        binding.buttonCodeEntfernen.setOnClickListener(v -> {
            code = null;
            codeType = CodeType.KEINER;
            bindCode();
        });
        binding.buttonSpeichern.setOnClickListener(v -> save());
        binding.chipGroupStatus.setOnCheckedStateChangeListener(
                (group, checkedIds) -> bindVerliehenFelder());

        loadLager();
        if (itemId != 0) {
            repository.loadItem(itemId, loaded -> {
                if (loaded == null) {
                    finish();
                    return;
                }
                item = loaded;
                bindItem(loaded);
            });
        } else {
            binding.editMenge.setText("1");
            bindCode();
            bindVerliehenFelder();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_item_edit, menu);
        MenuItem delete = menu.findItem(R.id.action_delete_item);
        delete.setVisible(itemId != 0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.action_delete_item && item != null) {
            Dialogs.confirm(this, getString(R.string.artikel_loeschen_titel),
                    getString(R.string.artikel_loeschen_text, item.name),
                    R.string.action_delete, () -> {
                        repository.deleteItem(item);
                        Toast.makeText(this, R.string.artikel_geloescht, Toast.LENGTH_SHORT).show();
                        finish();
                    });
            return true;
        }
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void loadLager() {
        repository.loadAllLager(list -> {
            lagerListe.clear();
            lagerListe.addAll(list);
            List<String> namen = new ArrayList<>();
            for (Lager lager : list) {
                namen.add(lager.name);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_list_item_1, namen);
            binding.dropdownLager.setAdapter(adapter);
            binding.dropdownLager.setOnItemClickListener(
                    (parent, view, position, id) -> selectedLagerId = lagerListe.get(position).id);
            bindLagerAuswahl();
        });
    }

    private void bindLagerAuswahl() {
        for (Lager lager : lagerListe) {
            if (lager.id == selectedLagerId) {
                binding.dropdownLager.setText(lager.name, false);
                return;
            }
        }
        if (!lagerListe.isEmpty()) {
            selectedLagerId = lagerListe.get(0).id;
            binding.dropdownLager.setText(lagerListe.get(0).name, false);
        }
    }

    private void bindItem(Item loaded) {
        binding.editName.setText(loaded.name);
        binding.editBeschreibung.setText(loaded.beschreibung);
        binding.editMenge.setText(String.valueOf(loaded.menge));
        binding.editNotiz.setText(loaded.notiz);
        binding.editVerliehenAn.setText(loaded.verliehenAn);
        code = loaded.code;
        codeType = loaded.codeType;
        selectedLagerId = loaded.lagerId;
        bindLagerAuswahl();
        bindCode();
        binding.chipGroupStatus.check(chipIdFor(loaded.status));
        bindVerliehenFelder();
    }

    private void bindCode() {
        if (code == null || code.isEmpty()) {
            binding.textCode.setText(R.string.artikel_code_leer);
            binding.textCodeType.setVisibility(View.GONE);
            binding.buttonCodeEntfernen.setVisibility(View.GONE);
        } else {
            binding.textCode.setText(code);
            binding.textCodeType.setVisibility(View.VISIBLE);
            binding.textCodeType.setText(codeType.labelRes);
            binding.buttonCodeEntfernen.setVisibility(View.VISIBLE);
        }
    }

    private void bindVerliehenFelder() {
        boolean verliehen = currentStatus() == ItemStatus.VERLIEHEN;
        binding.inputVerliehenAn.setVisibility(verliehen ? View.VISIBLE : View.GONE);
        if (verliehen && item != null && item.verliehenSeit != null) {
            binding.textVerliehenSeit.setVisibility(View.VISIBLE);
            binding.textVerliehenSeit.setText(getString(R.string.artikel_verliehen_seit,
                    Formatter.date(this, item.verliehenSeit)));
        } else {
            binding.textVerliehenSeit.setVisibility(View.GONE);
        }
    }

    private ItemStatus currentStatus() {
        int checked = binding.chipGroupStatus.getCheckedChipId();
        if (checked == R.id.chipVerliehen) {
            return ItemStatus.VERLIEHEN;
        }
        if (checked == R.id.chipVerloren) {
            return ItemStatus.VERLOREN;
        }
        return ItemStatus.VORHANDEN;
    }

    private int chipIdFor(ItemStatus status) {
        switch (status) {
            case VERLIEHEN:
                return R.id.chipVerliehen;
            case VERLOREN:
                return R.id.chipVerloren;
            default:
                return R.id.chipVorhanden;
        }
    }

    private void save() {
        String name = text(binding.editName.getText());
        if (name.isEmpty()) {
            binding.editName.setError(getString(R.string.artikel_name_fehlt));
            binding.editName.requestFocus();
            return;
        }
        if (selectedLagerId == 0) {
            Toast.makeText(this, R.string.treffer_kein_lager, Toast.LENGTH_LONG).show();
            return;
        }

        Item target = item == null ? new Item() : item.copy();
        target.name = name;
        target.lagerId = selectedLagerId;
        target.beschreibung = emptyToNull(text(binding.editBeschreibung.getText()));
        target.notiz = emptyToNull(text(binding.editNotiz.getText()));
        target.code = emptyToNull(code);
        target.codeType = target.code == null ? CodeType.KEINER : codeType;
        target.menge = parseMenge(text(binding.editMenge.getText()));
        target.status = currentStatus();

        if (target.status == ItemStatus.VERLIEHEN) {
            target.verliehenAn = emptyToNull(text(binding.editVerliehenAn.getText()));
            if (target.verliehenSeit == null) {
                target.verliehenSeit = System.currentTimeMillis();
            }
        } else {
            target.verliehenAn = null;
            target.verliehenSeit = null;
        }

        if (item == null) {
            repository.insertItem(target, id -> {
                Toast.makeText(this, R.string.artikel_gespeichert, Toast.LENGTH_SHORT).show();
                finish();
            });
        } else {
            repository.updateItem(target);
            Toast.makeText(this, R.string.artikel_gespeichert, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private int parseMenge(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Nullable
    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
