package de.tagstock.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import de.tagstock.R;
import de.tagstock.data.ItemStatus;
import de.tagstock.data.ItemWithState;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivitySucheBinding;
import de.tagstock.util.Dialogs;

/** Suche ueber alle Lager hinweg - Name, Beschreibung, Notiz und Codes. */
public class SucheActivity extends AppCompatActivity implements ItemAdapter.Listener {

    private ActivitySucheBinding binding;
    private Repository repository;
    private SucheViewModel viewModel;
    private ItemAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySucheBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = Repository.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setTitle(R.string.suche_titel);

        adapter = new ItemAdapter(this);
        binding.recyclerErgebnis.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerErgebnis.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(SucheViewModel.class);
        viewModel.getErgebnis().observe(this, treffer -> {
            adapter.submitList(treffer);
            boolean leer = treffer == null || treffer.isEmpty();
            binding.recyclerErgebnis.setVisibility(leer ? View.GONE : View.VISIBLE);
            binding.textHinweis.setVisibility(leer ? View.VISIBLE : View.GONE);
            binding.textHinweis.setText(viewModel.istLeererFilter()
                    ? R.string.suche_hinweis : R.string.suche_kein_treffer);
        });
        viewModel.getLagerNamen().observe(this, adapter::setLagerNamen);

        binding.editSuche.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setSuche(s == null ? "" : s.toString());
            }
        });

        binding.chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checked = group.getCheckedChipId();
            if (checked == R.id.chipVorhanden) {
                viewModel.setFilter(ItemStatus.VORHANDEN);
            } else if (checked == R.id.chipVerliehen) {
                viewModel.setFilter(ItemStatus.VERLIEHEN);
            } else if (checked == R.id.chipVerloren) {
                viewModel.setFilter(ItemStatus.VERLOREN);
            } else {
                viewModel.setFilter(null);
            }
        });

        binding.editSuche.requestFocus();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(ItemWithState item) {
        startActivity(ItemEditActivity.editIntent(this, item.item.id));
    }

    @Override
    public void onItemMenu(View anchor, ItemWithState state) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.menu_item_aktionen);
        popup.getMenu().findItem(R.id.aktion_ausleihen).setVisible(state.vorhanden() > 0);
        popup.getMenu().findItem(R.id.aktion_zurueck).setVisible(state.verliehen > 0);
        popup.getMenu().findItem(R.id.aktion_verloren).setVisible(state.vorhanden() > 0);
        popup.getMenu().findItem(R.id.aktion_gefunden).setVisible(state.verloren() > 0);
        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.aktion_ausleihen) {
                ItemAktionen.ausleihen(this, repository, state, null);
            } else if (id == R.id.aktion_zurueck) {
                ItemAktionen.zurueckgeben(this, repository, state.item.id, null);
            } else if (id == R.id.aktion_verloren) {
                ItemAktionen.verlorenMelden(this, repository, state, null);
            } else if (id == R.id.aktion_gefunden) {
                ItemAktionen.wiedergefunden(this, repository, state, null);
            } else if (id == R.id.aktion_bearbeiten) {
                startActivity(ItemEditActivity.editIntent(this, state.item.id));
            } else if (id == R.id.aktion_loeschen) {
                Dialogs.confirm(this, getString(R.string.artikel_loeschen_titel),
                        getString(R.string.artikel_loeschen_text, state.item.name),
                        R.string.action_delete, () -> {
                            repository.deleteItem(state.item);
                            Toast.makeText(this, R.string.artikel_geloescht,
                                    Toast.LENGTH_SHORT).show();
                        });
            }
            return true;
        });
        popup.show();
    }
}
