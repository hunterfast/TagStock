package de.tagstock.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.tagstock.R;
import de.tagstock.data.Item;
import de.tagstock.data.ItemStatus;
import de.tagstock.data.Lager;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityLagerDetailBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;

/** Artikelliste eines Lagers mit Suche, Statusfilter und Scanner. */
public class LagerDetailActivity extends AppCompatActivity implements ItemAdapter.Listener {

    private static final String EXTRA_LAGER_ID = "de.tagstock.extra.LAGER_ID";

    private ActivityLagerDetailBinding binding;
    private Repository repository;
    private ItemListViewModel viewModel;
    private ItemAdapter adapter;

    private long lagerId;
    private Lager lager;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    ScanResult scan = ScanResult.fromIntent(result.getData());
                    if (scan != null) {
                        handleScan(scan);
                    }
                }
            });

    public static Intent createIntent(Context context, long lagerId) {
        Intent intent = new Intent(context, LagerDetailActivity.class);
        intent.putExtra(EXTRA_LAGER_ID, lagerId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLagerDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = Repository.getInstance(this);

        lagerId = getIntent().getLongExtra(EXTRA_LAGER_ID, 0L);
        if (lagerId == 0L) {
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new ItemAdapter(this);
        binding.recyclerItems.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerItems.setAdapter(adapter);

        binding.fabScan.setOnClickListener(v -> scanLauncher.launch(ScannerActivity.createIntent(this)));

        viewModel = new ViewModelProvider(this).get(ItemListViewModel.class);
        viewModel.setLagerId(lagerId);
        viewModel.getItems().observe(this, items -> {
            adapter.submitList(items);
            boolean leer = items == null || items.isEmpty();
            binding.emptyView.setVisibility(leer ? View.VISIBLE : View.GONE);
            binding.recyclerItems.setVisibility(leer ? View.GONE : View.VISIBLE);
            if (leer && viewModel.hasItems()) {
                binding.textEmptyTitle.setText(R.string.artikel_leer_titel);
                binding.textEmptyText.setText(R.string.artikel_leer_filter);
            } else {
                binding.textEmptyTitle.setText(R.string.artikel_leer_titel);
                binding.textEmptyText.setText(R.string.artikel_leer_text);
            }
        });

        repository.observeLager(lagerId).observe(this, geladen -> {
            if (geladen == null) {
                finish();
                return;
            }
            lager = geladen;
            setTitle(geladen.name);
        });

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setQuery(s == null ? "" : s.toString());
            }
        });

        binding.chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checked = group.getCheckedChipId();
            if (checked == R.id.chipVorhanden) {
                viewModel.setStatusFilter(ItemStatus.VORHANDEN);
            } else if (checked == R.id.chipVerliehen) {
                viewModel.setStatusFilter(ItemStatus.VERLIEHEN);
            } else if (checked == R.id.chipVerloren) {
                viewModel.setStatusFilter(ItemStatus.VERLOREN);
            } else {
                viewModel.setStatusFilter(null);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        getMenuInflater().inflate(R.menu.menu_lager_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_add_item) {
            startActivity(ItemEditActivity.newIntent(this, lagerId, null));
            return true;
        }
        if (id == R.id.action_edit_lager && lager != null) {
            editLager();
            return true;
        }
        if (id == R.id.action_delete_lager && lager != null) {
            Dialogs.confirm(this, getString(R.string.lager_loeschen_titel),
                    getString(R.string.lager_loeschen_text, lager.name),
                    R.string.action_delete, () -> {
                        repository.deleteLager(lager);
                        Toast.makeText(this, R.string.lager_geloescht, Toast.LENGTH_SHORT).show();
                        finish();
                    });
            return true;
        }
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void editLager() {
        Dialogs.textInput(this, getString(R.string.lager_bearbeiten),
                getString(R.string.lager_name), lager.name, name -> {
                    lager.name = name;
                    repository.updateLager(lager);
                    setTitle(name);
                    Toast.makeText(this, R.string.lager_gespeichert, Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onItemClick(Item item) {
        startActivity(ItemEditActivity.editIntent(this, item.id));
    }

    @Override
    public void onItemMenu(View anchor, Item item) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.menu_item_status);
        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.status_vorhanden) {
                StatusActions.change(this, repository, item, ItemStatus.VORHANDEN);
            } else if (id == R.id.status_verliehen) {
                StatusActions.change(this, repository, item, ItemStatus.VERLIEHEN);
            } else if (id == R.id.status_verloren) {
                StatusActions.change(this, repository, item, ItemStatus.VERLOREN);
            } else if (id == R.id.item_bearbeiten) {
                startActivity(ItemEditActivity.editIntent(this, item.id));
            } else if (id == R.id.item_loeschen) {
                Dialogs.confirm(this, getString(R.string.artikel_loeschen_titel),
                        getString(R.string.artikel_loeschen_text, item.name),
                        R.string.action_delete, () -> {
                            repository.deleteItem(item);
                            Toast.makeText(this, R.string.artikel_geloescht, Toast.LENGTH_SHORT).show();
                        });
            }
            return true;
        });
        popup.show();
    }

    /** Gescannter Code: im Lager oeffnen, aus anderem Lager holen oder neu anlegen. */
    private void handleScan(ScanResult scan) {
        repository.findByCode(scan.code, item -> {
            if (item == null) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.treffer_nicht_gefunden_titel)
                        .setMessage(getString(R.string.treffer_nicht_gefunden_text, scan.code))
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.treffer_anlegen, (dialog, which) ->
                                startActivity(ItemEditActivity.newIntent(this, lagerId, scan)))
                        .show();
                return;
            }
            if (item.lagerId == lagerId) {
                Toast.makeText(this, getString(R.string.treffer_gefunden, item.name),
                        Toast.LENGTH_SHORT).show();
                startActivity(ItemEditActivity.editIntent(this, item.id));
                return;
            }
            askMove(item);
        });
    }

    private void askMove(Item item) {
        repository.loadLager(item.lagerId, anderes -> {
            String quelle = anderes == null ? "?" : anderes.name;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.treffer_anderes_lager_titel)
                    .setMessage(getString(R.string.treffer_anderes_lager_text, item.name, quelle))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setNeutralButton(R.string.treffer_oeffnen, (dialog, which) ->
                            startActivity(ItemEditActivity.editIntent(this, item.id)))
                    .setPositiveButton(R.string.treffer_verschieben, (dialog, which) -> {
                        Item verschoben = item.copy();
                        verschoben.lagerId = lagerId;
                        repository.updateItem(verschoben);
                        String ziel = lager == null ? "" : lager.name;
                        Toast.makeText(this, getString(R.string.treffer_verschoben, ziel),
                                Toast.LENGTH_SHORT).show();
                    })
                    .show();
        });
    }
}
