package de.tagstock.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ExperimentalGetImage;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.tagstock.R;
import de.tagstock.data.CodeType;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityInventurBinding;
import de.tagstock.databinding.ItemInventurBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.ScanResult;

/**
 * Dauerscan fuer die Bestandsaufnahme: Der Scanner bleibt offen, jeder Treffer
 * wandert in eine Liste. Am Ende wertet {@link InventurErgebnisActivity} aus,
 * was fehlt, was aus einem anderen Lager stammt und welche Codes unbekannt sind.
 */
@ExperimentalGetImage
public class InventurActivity extends ScannerBaseActivity {

    private static final String EXTRA_LAGER_ID = "de.tagstock.extra.LAGER_ID";

    private ActivityInventurBinding binding;
    private Repository repository;
    private long lagerId;

    private final List<Eintrag> eintraege = new ArrayList<>();
    private final Set<Long> gefunden = new LinkedHashSet<>();
    private final Set<Long> fremd = new LinkedHashSet<>();
    private final Set<String> unbekannt = new LinkedHashSet<>();
    private EintragAdapter adapter;

    public static Intent createIntent(Context context, long lagerId) {
        Intent intent = new Intent(context, InventurActivity.class);
        intent.putExtra(EXTRA_LAGER_ID, lagerId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInventurBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repository = Repository.getInstance(this);
        lagerId = getIntent().getLongExtra(EXTRA_LAGER_ID, 0L);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.buttonTorch.setOnClickListener(v -> blitzUmschalten());
        binding.buttonTorch.setVisibility(View.GONE);
        binding.buttonManuell.setOnClickListener(v -> Dialogs.textInput(this,
                getString(R.string.scan_manuell_titel), getString(R.string.artikel_code), null,
                wert -> onScan(new ScanResult(wert, CodeType.MANUELL, null))));
        binding.buttonAuswerten.setOnClickListener(v -> auswerten());

        adapter = new EintragAdapter();
        binding.recyclerEintraege.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerEintraege.setAdapter(adapter);

        scannerStarten(binding.previewView);
        zaehlerAktualisieren();
    }

    @Override
    protected void onKameraBereit() {
        binding.buttonTorch.setVisibility(hatBlitz() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onKameraFehlt() {
        binding.previewView.setVisibility(View.GONE);
        binding.buttonTorch.setVisibility(View.GONE);
        binding.textHinweis.setText(R.string.scan_hinweis_nur_nfc);
    }

    @Override
    protected void onScan(ScanResult ergebnis) {
        repository.findeZuCode(ergebnis.werte(), treffer -> {
            if (treffer == null) {
                if (unbekannt.add(ergebnis.code)) {
                    eintragHinzufuegen(new Eintrag(getString(R.string.inventur_unbekannt),
                            ergebnis.code, R.color.status_verloren));
                }
            } else if (treffer.item.item.lagerId == lagerId) {
                if (gefunden.add(treffer.item.item.id)) {
                    eintragHinzufuegen(new Eintrag(treffer.item.item.name,
                            getString(R.string.inventur_erfasst), R.color.status_vorhanden));
                }
            } else {
                if (fremd.add(treffer.item.item.id)) {
                    String lagerName = treffer.lager == null ? "?" : treffer.lager.name;
                    eintragHinzufuegen(new Eintrag(treffer.item.item.name,
                            getString(R.string.inventur_fremdes_lager, lagerName),
                            R.color.status_verliehen));
                }
            }
            zaehlerAktualisieren();
        });
    }

    private void eintragHinzufuegen(Eintrag eintrag) {
        eintraege.add(0, eintrag);
        adapter.notifyItemInserted(0);
        binding.recyclerEintraege.scrollToPosition(0);
    }

    private void zaehlerAktualisieren() {
        binding.textZaehler.setText(getString(R.string.inventur_zaehler,
                gefunden.size(), fremd.size(), unbekannt.size()));
        binding.buttonAuswerten.setEnabled(true);
    }

    private void auswerten() {
        if (gefunden.isEmpty() && fremd.isEmpty() && unbekannt.isEmpty()) {
            Toast.makeText(this, R.string.inventur_nichts_erfasst, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(InventurErgebnisActivity.createIntent(this, lagerId,
                alsArray(gefunden), alsArray(fremd),
                unbekannt.toArray(new String[0])));
        finish();
    }

    private long[] alsArray(Set<Long> werte) {
        long[] array = new long[werte.size()];
        int i = 0;
        for (Long wert : werte) {
            array[i++] = wert;
        }
        return array;
    }

    /** Ein Eintrag in der Liste der erfassten Codes. */
    private static class Eintrag {
        final String titel;
        final String untertitel;
        final int farbeRes;

        Eintrag(String titel, String untertitel, int farbeRes) {
            this.titel = titel;
            this.untertitel = untertitel;
            this.farbeRes = farbeRes;
        }
    }

    private class EintragAdapter extends RecyclerView.Adapter<EintragAdapter.Halter> {

        @NonNull
        @Override
        public Halter onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Halter(ItemInventurBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Halter holder, int position) {
            Eintrag eintrag = eintraege.get(position);
            holder.titel.setText(eintrag.titel);
            holder.untertitel.setText(eintrag.untertitel);
            holder.punkt.setBackgroundColor(
                    ContextCompat.getColor(InventurActivity.this, eintrag.farbeRes));
        }

        @Override
        public int getItemCount() {
            return eintraege.size();
        }

        class Halter extends RecyclerView.ViewHolder {
            final TextView titel;
            final TextView untertitel;
            final View punkt;

            Halter(ItemInventurBinding binding) {
                super(binding.getRoot());
                titel = binding.textTitel;
                untertitel = binding.textUntertitel;
                punkt = binding.viewPunkt;
            }
        }
    }
}
