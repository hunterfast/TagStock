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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.tagstock.R;
import de.tagstock.databinding.ActivityMitgliederBinding;
import de.tagstock.databinding.ItemMitgliedBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.Formatter;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.ServerClient;

/**
 * Wer gehoert zum Team und mit welcher Rolle. Admins aendern Rollen und
 * entfernen Konten; alle anderen sehen die Liste nur. Das letzte Adminkonto
 * laesst der Server weder herabstufen noch entfernen.
 */
public class MitgliederActivity extends AppCompatActivity {

    /** Was der Server ueber ein Mitglied weiss. */
    private static class Mitglied {
        String benutzerId;
        String name;
        String email;
        String rolle;
        long seit;
    }

    /** Liste und die eigene Kennung in einem Rutsch. */
    private static class Stand {
        String eigeneId;
        final List<Mitglied> mitglieder = new ArrayList<>();
    }

    private static final String[] ROLLEN = {"admin", "lagerist", "mitglied"};

    private ActivityMitgliederBinding binding;
    private final List<Mitglied> mitglieder = new ArrayList<>();
    private MitgliedAdapter adapter;
    private String eigeneId;
    private boolean istAdmin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Einstellungen.anwenden(this);
        super.onCreate(savedInstanceState);
        binding = ActivityMitgliederBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setTitle(R.string.mitglieder_titel);

        adapter = new MitgliedAdapter();
        binding.recyclerMitglieder.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMitglieder.setAdapter(adapter);

        istAdmin = "admin".equals(Einstellungen.rolle(this));
        binding.textNurLesen.setVisibility(istAdmin ? View.GONE : View.VISIBLE);
        laden();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------ Laden

    private void laden() {
        String teamId = Einstellungen.teamId(this);
        if (teamId == null) {
            Toast.makeText(this, R.string.server_kein_team, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        arbeitet(true);
        mitClient(client -> {
            Stand stand = new Stand();
            JSONObject ich = client.ich();
            JSONObject konto = ich.optJSONObject("benutzer");
            stand.eigeneId = konto == null ? null : konto.optString("id", null);
            JSONArray liste = client.mitglieder(teamId);
            for (int i = 0; i < liste.length(); i++) {
                JSONObject o = liste.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Mitglied mitglied = new Mitglied();
                mitglied.benutzerId = o.optString("benutzerId", "");
                mitglied.name = o.optString("name", "");
                mitglied.email = o.optString("email", "");
                mitglied.rolle = o.optString("rolle", "mitglied");
                mitglied.seit = o.optLong("seit", 0L);
                stand.mitglieder.add(mitglied);
            }
            return stand;
        }, stand -> {
            arbeitet(false);
            eigeneId = stand.eigeneId;
            mitglieder.clear();
            mitglieder.addAll(stand.mitglieder);
            adapter.notifyDataSetChanged();
            binding.textLeer.setVisibility(mitglieder.isEmpty() ? View.VISIBLE : View.GONE);
            eigeneRolleUebernehmen();
        });
    }

    /** Hat ein anderer Admin die eigene Rolle geaendert, gilt ab jetzt die neue. */
    private void eigeneRolleUebernehmen() {
        if (eigeneId == null) {
            return;
        }
        for (Mitglied mitglied : mitglieder) {
            if (eigeneId.equals(mitglied.benutzerId)
                    && !mitglied.rolle.equals(Einstellungen.rolle(this))) {
                Einstellungen.setzeTeam(this, Einstellungen.teamId(this),
                        Einstellungen.teamName(this), mitglied.rolle);
                istAdmin = "admin".equals(mitglied.rolle);
                binding.textNurLesen.setVisibility(istAdmin ? View.GONE : View.VISIBLE);
                adapter.notifyDataSetChanged();
                return;
            }
        }
    }

    // ------------------------------------------------------------- Aenderungen

    private void rolleWaehlen(Mitglied mitglied) {
        String[] beschriftungen = new String[ROLLEN.length];
        for (int i = 0; i < ROLLEN.length; i++) {
            beschriftungen[i] = rolleText(ROLLEN[i]);
        }
        int aktuell = 0;
        for (int i = 0; i < ROLLEN.length; i++) {
            if (ROLLEN[i].equals(mitglied.rolle)) {
                aktuell = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.mitglied_rolle_titel, mitglied.name))
                .setSingleChoiceItems(beschriftungen, aktuell, (dialog, welcher) -> {
                    dialog.dismiss();
                    if (!ROLLEN[welcher].equals(mitglied.rolle)) {
                        rolleSetzen(mitglied, ROLLEN[welcher]);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void rolleSetzen(Mitglied mitglied, String rolle) {
        String teamId = Einstellungen.teamId(this);
        arbeitet(true);
        mitClient(client -> {
            client.rolleSetzen(teamId, mitglied.benutzerId, rolle);
            return rolle;
        }, gesetzt -> {
            arbeitet(false);
            Toast.makeText(this, getString(R.string.mitglied_rolle_gesetzt, mitglied.name,
                    rolleText(gesetzt)), Toast.LENGTH_SHORT).show();
            laden();
        });
    }

    private void entfernen(Mitglied mitglied) {
        boolean selbst = mitglied.benutzerId.equals(eigeneId);
        Dialogs.confirm(this,
                getString(selbst ? R.string.mitglied_verlassen_titel
                        : R.string.mitglied_entfernen_titel),
                getString(selbst ? R.string.mitglied_verlassen_text
                        : R.string.mitglied_entfernen_text, mitglied.name),
                selbst ? R.string.mitglied_verlassen : R.string.mitglied_entfernen, () -> {
                    String teamId = Einstellungen.teamId(this);
                    arbeitet(true);
                    mitClient(client -> {
                        client.mitgliedEntfernen(teamId, mitglied.benutzerId);
                        return true;
                    }, fertig -> {
                        arbeitet(false);
                        if (selbst) {
                            Einstellungen.teamVerlassen(this);
                            Toast.makeText(this, R.string.mitglied_verlassen_fertig,
                                    Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                        laden();
                    });
                });
    }

    private String rolleText(String rolle) {
        switch (rolle) {
            case "admin":
                return getString(R.string.rolle_admin);
            case "lagerist":
                return getString(R.string.rolle_lagerist);
            default:
                return getString(R.string.rolle_mitglied);
        }
    }

    // ----------------------------------------------------------------- Technik

    private interface Aufruf<T> {
        T rufe(ServerClient client) throws Exception;
    }

    private <T> void mitClient(Aufruf<T> aufruf, Hintergrund.Fertig<T> fertig) {
        String url = Einstellungen.serverUrl(this);
        String token = Einstellungen.token(this);
        if (url == null || token == null) {
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
    }

    // ------------------------------------------------------------------ Liste

    private class MitgliedAdapter extends RecyclerView.Adapter<MitgliedAdapter.MitgliedHolder> {

        @NonNull
        @Override
        public MitgliedHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new MitgliedHolder(ItemMitgliedBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull MitgliedHolder holder, int position) {
            holder.bind(mitglieder.get(position));
        }

        @Override
        public int getItemCount() {
            return mitglieder.size();
        }

        class MitgliedHolder extends RecyclerView.ViewHolder {
            private final ItemMitgliedBinding binding;

            MitgliedHolder(ItemMitgliedBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(Mitglied mitglied) {
                boolean selbst = mitglied.benutzerId.equals(eigeneId);
                binding.textName.setText(selbst
                        ? getString(R.string.mitglied_du, mitglied.name) : mitglied.name);
                binding.textEmail.setText(mitglied.email);
                binding.textRolle.setText(mitglied.seit == 0
                        ? rolleText(mitglied.rolle)
                        : getString(R.string.mitglied_rolle_seit, rolleText(mitglied.rolle),
                        Formatter.datum(MitgliederActivity.this, mitglied.seit)));

                binding.getRoot().setOnClickListener(
                        istAdmin ? v -> rolleWaehlen(mitglied) : null);
                binding.getRoot().setClickable(istAdmin);
                binding.buttonEntfernen.setVisibility(
                        istAdmin || selbst ? View.VISIBLE : View.GONE);
                binding.buttonEntfernen.setOnClickListener(v -> entfernen(mitglied));
            }
        }
    }
}
