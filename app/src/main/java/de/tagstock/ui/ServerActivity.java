package de.tagstock.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import de.tagstock.R;
import de.tagstock.data.Abgleich;
import de.tagstock.databinding.ActivityServerBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.ServerClient;

/** Serveradresse, Anmeldung, Team-Auswahl und Abgleich. */
public class ServerActivity extends AppCompatActivity {

    private ActivityServerBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Einstellungen.anwenden(this);
        super.onCreate(savedInstanceState);
        binding = ActivityServerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setTitle(R.string.server_titel);

        String url = Einstellungen.serverUrl(this);
        binding.editServer.setText(url == null ? "http://" : url);

        binding.buttonPruefen.setOnClickListener(v -> pruefen());
        binding.buttonAnmelden.setOnClickListener(v -> anmelden(false));
        binding.buttonRegistrieren.setOnClickListener(v -> anmelden(true));
        binding.buttonAbmelden.setOnClickListener(v -> abmelden());
        binding.buttonTeamAnlegen.setOnClickListener(v -> teamAnlegen());
        binding.buttonTeamBeitreten.setOnClickListener(v -> teamBeitreten());
        binding.buttonTeamWechseln.setOnClickListener(v -> teamsZeigen());
        binding.buttonMitglieder.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, MitgliederActivity.class)));
        binding.buttonEinladung.setOnClickListener(v -> einladung());
        binding.buttonAbgleich.setOnClickListener(v -> abgleichen());

        zeichnen();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void zeichnen() {
        boolean angemeldet = Einstellungen.serverAktiv(this);
        binding.gruppeAnmeldung.setVisibility(angemeldet ? View.GONE : View.VISIBLE);
        binding.gruppeTeam.setVisibility(angemeldet ? View.VISIBLE : View.GONE);

        String team = Einstellungen.teamName(this);
        binding.textTeam.setText(team.isEmpty() ? getString(R.string.server_kein_team) : team);
        binding.textRolle.setText(getString(R.string.server_rolle,
                Einstellungen.rolle(this)));
        binding.buttonEinladung.setVisibility(
                "admin".equals(Einstellungen.rolle(this)) ? View.VISIBLE : View.GONE);
        binding.buttonMitglieder.setVisibility(
                Einstellungen.teamId(this) == null ? View.GONE : View.VISIBLE);

        long letzter = Einstellungen.letzterSync(this);
        binding.textLetzterSync.setText(letzter == 0
                ? getString(R.string.server_nie_abgeglichen)
                : getString(R.string.server_letzter_abgleich,
                de.tagstock.util.Formatter.datumZeit(this, letzter)));
    }

    private String adresse() {
        String url = binding.editServer.getText() == null
                ? "" : binding.editServer.getText().toString().trim();
        if (url.isEmpty() || url.equals("http://")) {
            Toast.makeText(this, R.string.server_adresse_fehlt, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return url;
    }

    private void arbeitet(boolean laeuft) {
        binding.fortschritt.setVisibility(laeuft ? View.VISIBLE : View.GONE);
    }

    private void fehler(Exception ausnahme) {
        arbeitet(false);
        String meldung = ausnahme.getMessage() == null
                ? getString(R.string.server_nicht_erreichbar) : ausnahme.getMessage();
        Toast.makeText(this, meldung, Toast.LENGTH_LONG).show();
    }

    // -------------------------------------------------------------- Verbindung

    private void pruefen() {
        String url = adresse();
        if (url == null) {
            return;
        }
        arbeitet(true);
        Hintergrund.starte(() -> new ServerClient(url, null).status(),
                antwort -> {
                    arbeitet(false);
                    Toast.makeText(this, getString(R.string.server_erreichbar,
                            antwort.optInt("apiVersion")), Toast.LENGTH_LONG).show();
                },
                this::fehler);
    }

    private void anmelden(boolean registrieren) {
        String url = adresse();
        if (url == null) {
            return;
        }
        String email = text(binding.editEmail.getText());
        String passwort = text(binding.editPasswort.getText());
        if (email.isEmpty() || passwort.isEmpty()) {
            Toast.makeText(this, R.string.server_zugang_fehlt, Toast.LENGTH_SHORT).show();
            return;
        }
        String name = text(binding.editName.getText());
        String code = text(binding.editCode.getText());

        arbeitet(true);
        Hintergrund.starte(() -> {
            ServerClient client = new ServerClient(url, null);
            return registrieren
                    ? client.registrieren(email, name.isEmpty() ? email : name, passwort, code)
                    : client.anmelden(email, passwort);
        }, antwort -> {
            arbeitet(false);
            Einstellungen.setzeServer(this, url, antwort.optString("token"));
            JSONArray teams = antwort.optJSONArray("teams");
            if (teams != null && teams.length() == 1) {
                teamUebernehmen(teams.optJSONObject(0));
            } else if (teams != null && teams.length() > 1) {
                teamsAuswaehlen(teams);
            }
            zeichnen();
            Toast.makeText(this, R.string.server_angemeldet, Toast.LENGTH_SHORT).show();
        }, this::fehler);
    }

    private void abmelden() {
        String url = Einstellungen.serverUrl(this);
        String token = Einstellungen.token(this);
        Einstellungen.abmelden(this);
        zeichnen();
        Toast.makeText(this, R.string.server_abgemeldet, Toast.LENGTH_SHORT).show();
        if (url != null && token != null) {
            Hintergrund.starte(() -> {
                new ServerClient(url, token).abmelden();
                return true;
            }, fertig -> {
            }, ausnahme -> {
                // Der Token ist lokal schon weg; ein Fehler stoert hier nicht.
            });
        }
    }

    // -------------------------------------------------------------------- Team

    private void teamsZeigen() {
        mitClient((client) -> client.teams(), this::teamsAuswaehlen);
    }

    private void teamsAuswaehlen(JSONArray teams) {
        if (teams == null || teams.length() == 0) {
            Toast.makeText(this, R.string.server_keine_teams, Toast.LENGTH_LONG).show();
            return;
        }
        String[] namen = new String[teams.length()];
        for (int i = 0; i < teams.length(); i++) {
            JSONObject team = teams.optJSONObject(i);
            namen[i] = team == null ? "?" : team.optString("name");
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.server_team_waehlen)
                .setItems(namen, (dialog, welcher) -> teamUebernehmen(teams.optJSONObject(welcher)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void teamUebernehmen(@Nullable JSONObject team) {
        if (team == null) {
            return;
        }
        Einstellungen.setzeTeam(this, team.optString("id"), team.optString("name"),
                team.optString("rolle", "mitglied"));
        // Nach einem Teamwechsel von vorn abgleichen.
        Einstellungen.setzeLetztenSync(this, 0L);
        zeichnen();
    }

    private void teamAnlegen() {
        Dialogs.textInput(this, getString(R.string.server_team_anlegen),
                getString(R.string.server_team_name), null,
                name -> mitClient(client -> client.teamAnlegen(name), team -> {
                    teamUebernehmen(team);
                    Toast.makeText(this, R.string.server_team_angelegt, Toast.LENGTH_SHORT).show();
                }));
    }

    private void teamBeitreten() {
        Dialogs.textInput(this, getString(R.string.server_team_beitreten),
                getString(R.string.server_code), null,
                code -> mitClient(client -> client.beitreten(code.trim().toUpperCase()), team -> {
                    teamUebernehmen(team);
                    Toast.makeText(this, R.string.server_beigetreten, Toast.LENGTH_SHORT).show();
                }));
    }

    private void einladung() {
        String teamId = Einstellungen.teamId(this);
        if (teamId == null) {
            return;
        }
        String[] rollen = {"mitglied", "lagerist", "admin"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.server_einladung_rolle)
                .setItems(rollen, (dialog, welcher) ->
                        mitClient(client -> client.einladung(teamId, rollen[welcher]),
                                antwort -> new MaterialAlertDialogBuilder(this)
                                        .setTitle(R.string.server_einladung_titel)
                                        .setMessage(getString(R.string.server_einladung_text,
                                                antwort.optString("code"),
                                                antwort.optString("rolle")))
                                        .setPositiveButton(R.string.action_ok, null)
                                        .show()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void abgleichen() {
        arbeitet(true);
        Abgleich.ausfuehren(this, ergebnis -> {
            arbeitet(false);
            zeichnen();
            if (!ergebnis.erfolgreich()) {
                Toast.makeText(this, ergebnis.fehler, Toast.LENGTH_LONG).show();
                if (ergebnis.abgemeldet) {
                    Einstellungen.abmelden(this);
                    zeichnen();
                }
                return;
            }
            String meldung = getString(R.string.server_abgleich_fertig,
                    ergebnis.hochgeladen, ergebnis.uebernommen, ergebnis.abgelehnt);
            if (ergebnis.bilder > 0) {
                meldung += getString(R.string.server_abgleich_bilder, ergebnis.bilder);
            }
            if (!ergebnis.gruende.isEmpty()) {
                meldung += "\n" + getString(R.string.sync_abgelehnt_hinweis, ergebnis.abgelehnt,
                        ergebnis.gruende.get(0));
            }
            Toast.makeText(this, meldung, Toast.LENGTH_LONG).show();
        });
    }

    // ----------------------------------------------------------------- Technik

    private interface Aufruf<T> {
        T rufe(ServerClient client) throws Exception;
    }

    /** Fuehrt einen Serveraufruf mit den hinterlegten Zugangsdaten aus. */
    private <T> void mitClient(Aufruf<T> aufruf, Hintergrund.Fertig<T> fertig) {
        String url = Einstellungen.serverUrl(this);
        String token = Einstellungen.token(this);
        if (url == null || token == null) {
            Toast.makeText(this, R.string.server_nicht_angemeldet, Toast.LENGTH_SHORT).show();
            return;
        }
        arbeitet(true);
        Hintergrund.starte(() -> aufruf.rufe(new ServerClient(url, token)), ergebnis -> {
            arbeitet(false);
            fertig.onFertig(ergebnis);
        }, this::fehler);
    }

    private String text(CharSequence wert) {
        return wert == null ? "" : wert.toString().trim();
    }
}
