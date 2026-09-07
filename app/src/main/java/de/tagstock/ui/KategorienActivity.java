package de.tagstock.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.tagstock.R;
import de.tagstock.data.Kategorie;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityKategorienBinding;
import de.tagstock.databinding.ItemKategorieBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Einstellungen;

/** Kategorien anlegen, umbenennen, loeschen und sortieren. */
public class KategorienActivity extends AppCompatActivity {

    private ActivityKategorienBinding binding;
    private Repository repository;
    private final List<Kategorie> kategorien = new ArrayList<>();
    private KategorieAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Einstellungen.anwenden(this);
        super.onCreate(savedInstanceState);
        binding = ActivityKategorienBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = Repository.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setTitle(R.string.kategorien_titel);

        adapter = new KategorieAdapter();
        binding.recyclerKategorien.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerKategorien.setAdapter(adapter);

        binding.fabNeu.setOnClickListener(v -> Dialogs.textInput(this,
                getString(R.string.kategorie_neu), getString(R.string.kategorie_name), null,
                name -> repository.kategorieAnlegen(name, erfolg -> {
                    if (!erfolg) {
                        Toast.makeText(this, R.string.kategorie_doppelt, Toast.LENGTH_SHORT).show();
                    }
                })));

        repository.beobachteKategorien().observe(this, liste -> {
            kategorien.clear();
            if (liste != null) {
                kategorien.addAll(liste);
            }
            adapter.notifyDataSetChanged();
            binding.textLeer.setVisibility(kategorien.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void verschieben(int von, int nach) {
        if (nach < 0 || nach >= kategorien.size()) {
            return;
        }
        Kategorie kategorie = kategorien.remove(von);
        kategorien.add(nach, kategorie);
        adapter.notifyItemMoved(von, nach);
        repository.kategorienVerschieben(new ArrayList<>(kategorien));
    }

    private class KategorieAdapter extends RecyclerView.Adapter<KategorieAdapter.KategorieHolder> {

        @NonNull
        @Override
        public KategorieHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new KategorieHolder(ItemKategorieBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull KategorieHolder holder, int position) {
            holder.bind(kategorien.get(position), position);
        }

        @Override
        public int getItemCount() {
            return kategorien.size();
        }

        class KategorieHolder extends RecyclerView.ViewHolder {
            private final ItemKategorieBinding binding;

            KategorieHolder(ItemKategorieBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(Kategorie kategorie, int position) {
                binding.textName.setText(kategorie.name);
                binding.buttonHoch.setEnabled(position > 0);
                binding.buttonRunter.setEnabled(position < kategorien.size() - 1);
                binding.buttonHoch.setOnClickListener(v -> verschieben(position, position - 1));
                binding.buttonRunter.setOnClickListener(v -> verschieben(position, position + 1));
                binding.getRoot().setOnClickListener(v -> Dialogs.textInput(
                        KategorienActivity.this, getString(R.string.kategorie_umbenennen),
                        getString(R.string.kategorie_name), kategorie.name,
                        name -> repository.kategorieUmbenennen(kategorie, name)));
                binding.buttonLoeschen.setOnClickListener(v -> Dialogs.confirm(
                        KategorienActivity.this, getString(R.string.kategorie_loeschen_titel),
                        getString(R.string.kategorie_loeschen_text, kategorie.name),
                        R.string.action_delete, () -> repository.kategorieLoeschen(kategorie)));
            }
        }
    }
}
