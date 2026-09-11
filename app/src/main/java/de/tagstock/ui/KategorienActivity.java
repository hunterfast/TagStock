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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.tagstock.R;
import de.tagstock.data.Kategorie;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityKategorienBinding;
import de.tagstock.databinding.ItemKategorieBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.Randabstand;
import de.tagstock.util.ServerClient;

/**
 * Kategorien anlegen, umbenennen, loeschen und sortieren. Haengt die App an
 * einem Server, gilt die Rolle: nur Admins und Lageristen duerfen aendern, und
 * jede Aenderung geht zuerst an den Server, damit alle Geraete sie bekommen.
 */
public class KategorienActivity extends AppCompatActivity {

    private ActivityKategorienBinding binding;
    private Repository repository;
    private final List<Kategorie> kategorien = new ArrayList<>();
    private KategorieAdapter adapter;
    private boolean darfBearbeiten;
    private boolean amServer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Einstellungen.anwenden(this);
        super.onCreate(savedInstanceState);
        binding = ActivityKategorienBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Randabstand.anwenden(binding.getRoot());
        repository = Repository.getInstance(this);
        darfBearbeiten = Einstellungen.darfBearbeiten(this);
        amServer = Einstellungen.serverAktiv(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setTitle(R.string.kategorien_titel);

        adapter = new KategorieAdapter();
        binding.recyclerKategorien.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerKategorien.setAdapter(adapter);

        binding.fabNeu.setVisibility(darfBearbeiten ? View.VISIBLE : View.GONE);
        binding.textNurLesen.setVisibility(darfBearbeiten ? View.GONE : View.VISIBLE);
        binding.fabNeu.setOnClickListener(v -> Dialogs.textInput(this,
                getString(R.string.kategorie_neu), getString(R.string.kategorie_name), null,
                this::anlegen));

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

    // ------------------------------------------------------------- Aenderungen

    private void anlegen(String name) {
        if (!amServer) {
            repository.kategorieAnlegen(name, this::gemeldet);
            return;
        }
        String teamId = Einstellungen.teamId(this);
        arbeitet(true);
        mitClient(client -> client.kategorieAnlegen(teamId, name, kategorien.size()),
                antwort -> {
                    arbeitet(false);
                    String serverId = antwort == null ? null : antwort.optString("id", null);
                    repository.kategorieAnlegen(name, serverId, this::gemeldet);
                });
    }

    private void umbenennen(Kategorie kategorie, String name) {
        if (!amServer) {
            repository.kategorieUmbenennen(kategorie, name);
            return;
        }
        String teamId = Einstellungen.teamId(this);
        arbeitet(true);
        mitClient(client -> {
            String serverId = serverKennung(client, teamId, kategorie);
            if (serverId != null) {
                client.kategorieAendern(teamId, serverId, name, kategorie.reihenfolge);
            }
            return serverId;
        }, serverId -> {
            arbeitet(false);
            if (serverId != null) {
                kategorie.serverId = serverId;
            }
            repository.kategorieUmbenennen(kategorie, name);
        });
    }

    private void loeschen(Kategorie kategorie) {
        if (!amServer) {
            repository.kategorieLoeschen(kategorie);
            return;
        }
        String teamId = Einstellungen.teamId(this);
        arbeitet(true);
        mitClient(client -> {
            String serverId = serverKennung(client, teamId, kategorie);
            if (serverId != null) {
                client.kategorieLoeschen(teamId, serverId);
            }
            return true;
        }, fertig -> {
            arbeitet(false);
            repository.kategorieLoeschen(kategorie);
        });
    }

    private void verschieben(int von, int nach) {
        if (nach < 0 || nach >= kategorien.size()) {
            return;
        }
        Kategorie kategorie = kategorien.remove(von);
        kategorien.add(nach, kategorie);
        adapter.notifyItemMoved(von, nach);
        List<Kategorie> neueFolge = new ArrayList<>(kategorien);
        repository.kategorienVerschieben(neueFolge);

        if (!amServer) {
            return;
        }
        String teamId = Einstellungen.teamId(this);
        mitClient(client -> {
            for (int i = 0; i < neueFolge.size(); i++) {
                Kategorie eintrag = neueFolge.get(i);
                if (eintrag.serverId != null && !eintrag.serverId.isEmpty()) {
                    client.kategorieAendern(teamId, eintrag.serverId, eintrag.name, i);
                }
            }
            return true;
        }, fertig -> { });
    }

    private void gemeldet(Boolean erfolg) {
        if (Boolean.FALSE.equals(erfolg)) {
            Toast.makeText(this, R.string.kategorie_doppelt, Toast.LENGTH_SHORT).show();
        }
    }

    // ----------------------------------------------------------------- Technik

    /** Sucht die Kennung auf dem Server - notfalls ueber den Namen. */
    @Nullable
    private static String serverKennung(ServerClient client, String teamId, Kategorie kategorie)
            throws Exception {
        if (kategorie.serverId != null && !kategorie.serverId.isEmpty()) {
            return kategorie.serverId;
        }
        JSONArray liste = client.kategorien(teamId);
        for (int i = 0; i < liste.length(); i++) {
            JSONObject eintrag = liste.optJSONObject(i);
            if (eintrag != null && kategorie.name.equalsIgnoreCase(eintrag.optString("name"))) {
                String id = eintrag.optString("id", "");
                return id.isEmpty() ? null : id;
            }
        }
        return null;
    }

    private interface Aufruf<T> {
        T rufe(ServerClient client) throws Exception;
    }

    private <T> void mitClient(Aufruf<T> aufruf, Hintergrund.Fertig<T> fertig) {
        String url = Einstellungen.serverUrl(this);
        String token = Einstellungen.token(this);
        if (url == null || token == null || Einstellungen.teamId(this) == null) {
            arbeitet(false);
            Toast.makeText(this, R.string.server_nicht_eingerichtet, Toast.LENGTH_SHORT).show();
            return;
        }
        Hintergrund.starte(() -> aufruf.rufe(new ServerClient(url, token)), fertig, fehler -> {
            arbeitet(false);
            String meldung = fehler.getMessage();
            Toast.makeText(this, meldung == null
                    ? getString(R.string.server_nicht_erreichbar) : meldung,
                    Toast.LENGTH_LONG).show();
        });
    }

    private void arbeitet(boolean laeuft) {
        binding.fortschritt.setVisibility(laeuft ? View.VISIBLE : View.GONE);
        binding.fabNeu.setEnabled(!laeuft);
    }

    // ------------------------------------------------------------------ Liste

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
                binding.buttonHoch.setVisibility(darfBearbeiten ? View.VISIBLE : View.GONE);
                binding.buttonRunter.setVisibility(darfBearbeiten ? View.VISIBLE : View.GONE);
                binding.buttonLoeschen.setVisibility(darfBearbeiten ? View.VISIBLE : View.GONE);
                binding.buttonHoch.setEnabled(position > 0);
                binding.buttonRunter.setEnabled(position < kategorien.size() - 1);
                binding.buttonHoch.setOnClickListener(v -> verschieben(position, position - 1));
                binding.buttonRunter.setOnClickListener(v -> verschieben(position, position + 1));
                binding.getRoot().setOnClickListener(darfBearbeiten ? v -> Dialogs.textInput(
                        KategorienActivity.this, getString(R.string.kategorie_umbenennen),
                        getString(R.string.kategorie_name), kategorie.name,
                        name -> umbenennen(kategorie, name)) : null);
                binding.getRoot().setClickable(darfBearbeiten);
                binding.buttonLoeschen.setOnClickListener(v -> Dialogs.confirm(
                        KategorienActivity.this, getString(R.string.kategorie_loeschen_titel),
                        getString(R.string.kategorie_loeschen_text, kategorie.name),
                        R.string.action_delete, () -> loeschen(kategorie)));
            }
        }
    }
}
