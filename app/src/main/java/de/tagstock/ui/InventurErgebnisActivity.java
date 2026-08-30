package de.tagstock.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.tagstock.R;
import de.tagstock.data.Item;
import de.tagstock.data.ItemWithState;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityInventurErgebnisBinding;
import de.tagstock.util.ScanResult;

/**
 * Auswertung einer Bestandsaufnahme: was gefunden wurde, was fehlt, was aus
 * einem anderen Lager stammt und welche Codes noch keinem Artikel gehoeren.
 */
public class InventurErgebnisActivity extends AppCompatActivity {

    private static final String EXTRA_LAGER_ID = "de.tagstock.extra.LAGER_ID";
    private static final String EXTRA_GEFUNDEN = "de.tagstock.extra.GEFUNDEN";
    private static final String EXTRA_FREMD = "de.tagstock.extra.FREMD";
    private static final String EXTRA_UNBEKANNT = "de.tagstock.extra.UNBEKANNT";

    private ActivityInventurErgebnisBinding binding;
    private Repository repository;

    private long lagerId;
    private Set<Long> gefunden;
    private final List<ItemWithState> fehlend = new ArrayList<>();
    private final List<ItemWithState> fremd = new ArrayList<>();
    private final List<String> unbekannt = new ArrayList<>();

    public static Intent createIntent(Context context, long lagerId, long[] gefunden,
                                      long[] fremd, String[] unbekannt) {
        Intent intent = new Intent(context, InventurErgebnisActivity.class);
        intent.putExtra(EXTRA_LAGER_ID, lagerId);
        intent.putExtra(EXTRA_GEFUNDEN, gefunden);
        intent.putExtra(EXTRA_FREMD, fremd);
        intent.putExtra(EXTRA_UNBEKANNT, unbekannt);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInventurErgebnisBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = Repository.getInstance(this);

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        setTitle(R.string.inventur_ergebnis_titel);

        lagerId = getIntent().getLongExtra(EXTRA_LAGER_ID, 0L);
        long[] gefundenIds = getIntent().getLongArrayExtra(EXTRA_GEFUNDEN);
        gefunden = new HashSet<>();
        if (gefundenIds != null) {
            for (long id : gefundenIds) {
                gefunden.add(id);
            }
        }
        String[] unbekannteCodes = getIntent().getStringArrayExtra(EXTRA_UNBEKANNT);
        if (unbekannteCodes != null) {
            unbekannt.addAll(Arrays.asList(unbekannteCodes));
        }

        binding.buttonVerloren.setOnClickListener(v -> verlorenMelden());
        binding.buttonVerschieben.setOnClickListener(v -> verschieben());
        binding.buttonAnlegen.setOnClickListener(v -> anlegen());

        laden(getIntent().getLongArrayExtra(EXTRA_FREMD));
    }

    @Override
    protected void onResume() {
        super.onResume();
        laden(null);
    }

    /** Liest den aktuellen Bestand und teilt ihn in gefunden, fehlend und fremd auf. */
    private void laden(@Nullable long[] fremdIds) {
        Set<Long> fremdeIds = new HashSet<>();
        if (fremdIds != null) {
            for (long id : fremdIds) {
                fremdeIds.add(id);
            }
        } else {
            for (ItemWithState state : fremd) {
                fremdeIds.add(state.item.id);
            }
        }

        repository.ladeAlleZustaende(alle -> {
            fehlend.clear();
            fremd.clear();
            for (ItemWithState state : alle) {
                if (state.item.lagerId == lagerId) {
                    // Vollstaendig verliehene oder verlorene Stuecke koennen im
                    // Regal gar nicht liegen und fehlen deshalb nicht.
                    if (!gefunden.contains(state.item.id) && state.vorhanden() > 0) {
                        fehlend.add(state);
                    }
                } else if (fremdeIds.contains(state.item.id)) {
                    fremd.add(state);
                }
            }
            anzeigen();
        });
    }

    private void anzeigen() {
        binding.textGefunden.setText(getString(R.string.inventur_gefunden_anzahl, gefunden.size()));

        binding.textFehlend.setText(fehlend.isEmpty()
                ? getString(R.string.inventur_nichts_fehlt)
                : namen(fehlend));
        binding.cardFehlend.setVisibility(fehlend.isEmpty() ? View.GONE : View.VISIBLE);
        binding.buttonVerloren.setEnabled(!fehlend.isEmpty());
        binding.textFehlendTitel.setText(
                getString(R.string.inventur_fehlt_titel, fehlend.size()));

        binding.cardFremd.setVisibility(fremd.isEmpty() ? View.GONE : View.VISIBLE);
        binding.textFremd.setText(namen(fremd));
        binding.textFremdTitel.setText(getString(R.string.inventur_fremd_titel, fremd.size()));

        binding.cardUnbekannt.setVisibility(unbekannt.isEmpty() ? View.GONE : View.VISIBLE);
        binding.textUnbekannt.setText(zeilen(unbekannt));
        binding.textUnbekanntTitel.setText(
                getString(R.string.inventur_unbekannt_titel, unbekannt.size()));
    }

    private String zeilen(List<String> werte) {
        StringBuilder text = new StringBuilder();
        for (String wert : werte) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(wert);
        }
        return text.toString();
    }

    private String namen(List<ItemWithState> liste) {
        StringBuilder text = new StringBuilder();
        for (ItemWithState state : liste) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(state.item.name);
        }
        return text.toString();
    }

    private void verlorenMelden() {
        auswahlDialog(R.string.inventur_verloren_titel, namenArray(fehlend), auswahl -> {
            int[] offen = {0};
            for (int i = 0; i < fehlend.size(); i++) {
                if (!auswahl[i]) {
                    continue;
                }
                ItemWithState state = fehlend.get(i);
                offen[0]++;
                repository.verlorenMelden(state.item.id, state.vorhanden(), erfolg -> {
                    if (--offen[0] <= 0) {
                        Toast.makeText(this, R.string.inventur_verloren_gemeldet,
                                Toast.LENGTH_SHORT).show();
                        laden(null);
                    }
                });
            }
            if (offen[0] == 0) {
                laden(null);
            }
        });
    }

    private void verschieben() {
        auswahlDialog(R.string.inventur_verschieben_titel, namenArray(fremd), auswahl -> {
            for (int i = 0; i < fremd.size(); i++) {
                if (!auswahl[i]) {
                    continue;
                }
                Item item = fremd.get(i).item.copy();
                item.lagerId = lagerId;
                repository.updateItem(item);
            }
            Toast.makeText(this, R.string.inventur_verschoben, Toast.LENGTH_SHORT).show();
            laden(null);
        });
    }

    private void anlegen() {
        String[] codes = unbekannt.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.inventur_anlegen_titel)
                .setItems(codes, (dialog, welcher) -> {
                    String code = codes[welcher];
                    unbekannt.remove(code);
                    startActivity(ItemEditActivity.newIntent(this, lagerId,
                            new ScanResult(code, de.tagstock.data.CodeType.MANUELL, null)));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private String[] namenArray(List<ItemWithState> liste) {
        String[] namen = new String[liste.size()];
        for (int i = 0; i < liste.size(); i++) {
            namen[i] = liste.get(i).item.name;
        }
        return namen;
    }

    private interface AuswahlCallback {
        void onAuswahl(boolean[] auswahl);
    }

    /** Mehrfachauswahl, standardmaessig ist alles angehakt. */
    private void auswahlDialog(int titelRes, String[] namen, AuswahlCallback callback) {
        boolean[] auswahl = new boolean[namen.length];
        Arrays.fill(auswahl, true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(titelRes)
                .setMultiChoiceItems(namen, auswahl,
                        (dialog, welcher, angehakt) -> auswahl[welcher] = angehakt)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, welcher) -> callback.onAuswahl(auswahl))
                .show();
    }
}
