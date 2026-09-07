package de.tagstock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.tagstock.R;
import de.tagstock.databinding.FragmentAnfragenBinding;
import de.tagstock.databinding.ItemAnfrageBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.Formatter;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.ServerClient;

/**
 * Leih-Anfragen des Teams. Sie brauchen den TagStock-Server, weil mehrere
 * Personen beteiligt sind; ohne Serverzugang erklaert der Bereich, was fehlt.
 */
public class AnfragenFragment extends Fragment {

    private FragmentAnfragenBinding binding;
    private final List<JSONObject> anfragen = new ArrayList<>();
    private AnfrageAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAnfragenBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.buttonEinrichten.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ServerActivity.class)));

        adapter = new AnfrageAdapter();
        binding.recyclerAnfragen.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerAnfragen.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::laden);
    }

    @Override
    public void onResume() {
        super.onResume();
        zeichnen();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            zeichnen();
        }
    }

    private void zeichnen() {
        if (binding == null) {
            return;
        }
        boolean server = Einstellungen.serverAktiv(requireContext())
                && Einstellungen.teamId(requireContext()) != null;
        binding.gruppeOhneServer.setVisibility(server ? View.GONE : View.VISIBLE);
        binding.gruppeMitServer.setVisibility(server ? View.VISIBLE : View.GONE);
        if (server) {
            laden();
        }
    }

    private void laden() {
        String url = Einstellungen.serverUrl(requireContext());
        String token = Einstellungen.token(requireContext());
        String teamId = Einstellungen.teamId(requireContext());
        if (url == null || token == null || teamId == null) {
            return;
        }
        binding.swipeRefresh.setRefreshing(true);
        Hintergrund.starte(() -> new ServerClient(url, token).anfragen(teamId), liste -> {
            binding.swipeRefresh.setRefreshing(false);
            anfragen.clear();
            for (int i = 0; i < liste.length(); i++) {
                JSONObject anfrage = liste.optJSONObject(i);
                if (anfrage != null) {
                    anfragen.add(anfrage);
                }
            }
            adapter.notifyDataSetChanged();
            binding.textKeineAnfragen.setVisibility(
                    anfragen.isEmpty() ? View.VISIBLE : View.GONE);
        }, fehler -> {
            binding.swipeRefresh.setRefreshing(false);
            Toast.makeText(requireContext(), fehler.getMessage() == null
                            ? getString(R.string.server_nicht_erreichbar) : fehler.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    private void entscheiden(JSONObject anfrage, boolean genehmigt) {
        String url = Einstellungen.serverUrl(requireContext());
        String token = Einstellungen.token(requireContext());
        String teamId = Einstellungen.teamId(requireContext());
        if (url == null || token == null || teamId == null) {
            return;
        }
        Dialogs.textInput(requireContext(),
                getString(genehmigt ? R.string.anfrage_genehmigen : R.string.anfrage_ablehnen),
                getString(R.string.anfrage_antwort), null,
                antwort -> Hintergrund.starte(
                        () -> new ServerClient(url, token).anfrageEntscheiden(teamId,
                                anfrage.optString("id"), genehmigt, antwort),
                        ergebnis -> {
                            Toast.makeText(requireContext(), genehmigt
                                            ? R.string.anfrage_genehmigt : R.string.anfrage_abgelehnt,
                                    Toast.LENGTH_SHORT).show();
                            laden();
                        },
                        fehler -> Toast.makeText(requireContext(), String.valueOf(fehler.getMessage()),
                                Toast.LENGTH_LONG).show()));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private class AnfrageAdapter extends RecyclerView.Adapter<AnfrageAdapter.AnfrageHolder> {

        @NonNull
        @Override
        public AnfrageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new AnfrageHolder(ItemAnfrageBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull AnfrageHolder holder, int position) {
            holder.bind(anfragen.get(position));
        }

        @Override
        public int getItemCount() {
            return anfragen.size();
        }

        class AnfrageHolder extends RecyclerView.ViewHolder {
            private final ItemAnfrageBinding binding;

            AnfrageHolder(ItemAnfrageBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(JSONObject anfrage) {
                binding.textArtikel.setText(anfrage.optString("artikelName"));
                binding.textPerson.setText(getString(R.string.anfrage_von,
                        anfrage.optString("antragstellerName")));

                String nachricht = anfrage.optString("nachricht", "");
                binding.textNachricht.setVisibility(nachricht.isEmpty() || "null".equals(nachricht)
                        ? View.GONE : View.VISIBLE);
                binding.textNachricht.setText(nachricht);

                long bis = anfrage.optLong("datumBis", 0);
                binding.textZeitraum.setVisibility(bis == 0 ? View.GONE : View.VISIBLE);
                if (bis != 0) {
                    binding.textZeitraum.setText(getString(R.string.anfrage_bis,
                            Formatter.datum(requireContext(), bis)));
                }

                String status = anfrage.optString("status", "offen");
                binding.textStatus.setText(status);
                boolean offen = "offen".equals(status);
                boolean darf = Einstellungen.darfBearbeiten(requireContext());

                binding.buttonGenehmigen.setVisibility(offen && darf ? View.VISIBLE : View.GONE);
                binding.buttonAblehnen.setVisibility(offen && darf ? View.VISIBLE : View.GONE);
                binding.buttonGenehmigen.setOnClickListener(v -> entscheiden(anfrage, true));
                binding.buttonAblehnen.setOnClickListener(v -> entscheiden(anfrage, false));

                String antwort = anfrage.optString("antwort", "");
                binding.textAntwort.setVisibility(antwort.isEmpty() || "null".equals(antwort)
                        ? View.GONE : View.VISIBLE);
                binding.textAntwort.setText(antwort);
            }
        }
    }
}
