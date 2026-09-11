package de.tagstock.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.tagstock.R;
import de.tagstock.data.Abgleich;
import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.Repository;
import de.tagstock.databinding.FragmentBestandBinding;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.Formatter;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.PdfErzeuger;
import de.tagstock.util.ScanResult;
import de.tagstock.util.Sicherung;

/** Bestandsliste mit Zahlen, Warnungen, Filtern und Mehrfachauswahl. */
public class BestandFragment extends Fragment implements ArtikelAdapter.Listener {

    private FragmentBestandBinding binding;
    private BestandViewModel viewModel;
    private ArtikelAdapter adapter;
    private Repository repository;

    private final List<Artikel> aktuelleListe = new ArrayList<>();
    private boolean ueberfaelligVerborgen;
    private boolean ueberfaelligOffen;
    private boolean vermisstOffen;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), ergebnis -> {
                if (ergebnis.getResultCode() != android.app.Activity.RESULT_OK) {
                    return;
                }
                ScanResult scan = ScanResult.fromIntent(ergebnis.getData());
                if (scan != null) {
                    binding.editSuche.setText(scan.code);
                }
            });

    private final ActivityResultLauncher<String> etikettenLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), this::etikettenSchreiben);

    private List<Artikel> etikettenAuswahl = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBestandBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = Repository.getInstance(requireContext());
        viewModel = new ViewModelProvider(this).get(BestandViewModel.class);

        adapter = new ArtikelAdapter(this);
        binding.recyclerArtikel.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerArtikel.setAdapter(adapter);

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

        binding.buttonScanSuche.setOnClickListener(v ->
                scanLauncher.launch(ScannerActivity.intent(requireContext())));

        binding.cardGesamt.setOnClickListener(v -> statusFilter(null));
        binding.cardVorhanden.setOnClickListener(v -> statusFilter(ArtikelStatus.VORHANDEN));
        binding.cardFehlt.setOnClickListener(v -> statusFilter(ArtikelStatus.NICHT_VORHANDEN));
        binding.cardVerliehen.setOnClickListener(v -> statusFilter(ArtikelStatus.VERLIEHEN));

        binding.buttonUeberfaelligAusklappen.setOnClickListener(v -> {
            ueberfaelligOffen = !ueberfaelligOffen;
            bannerZeichnen(aktuelleListe);
        });
        binding.buttonUeberfaelligSchliessen.setOnClickListener(v -> {
            ueberfaelligVerborgen = true;
            binding.cardUeberfaellig.setVisibility(View.GONE);
        });
        binding.headerVermisst.setOnClickListener(v -> {
            vermisstOffen = !vermisstOffen;
            bannerZeichnen(aktuelleListe);
        });

        binding.buttonAuswahl.setOnClickListener(v -> auswahlUmschalten());
        binding.buttonAuswahlAbbrechen.setOnClickListener(v -> auswahlBeenden());
        binding.buttonAuswahlStatus.setOnClickListener(v -> statusFuerAuswahl());
        binding.buttonAuswahlStandort.setOnClickListener(v -> standortFuerAuswahl());
        binding.buttonAuswahlEtiketten.setOnClickListener(v -> etikettenDrucken());

        binding.swipeRefresh.setOnRefreshListener(this::abgleichen);

        // Die Behaelternamen kommen aus der ungefilterten Liste: Wer in einer
        // Box liegt, soll deren Namen sehen, auch wenn die Box weggefiltert ist.
        viewModel.getAlle().observe(getViewLifecycleOwner(), adapter::behaelterKennen);

        viewModel.getGefiltert().observe(getViewLifecycleOwner(), artikel -> {
            adapter.submitList(artikel);
            boolean leer = artikel == null || artikel.isEmpty();
            binding.recyclerArtikel.setVisibility(leer ? View.GONE : View.VISIBLE);
            binding.leerAnsicht.setVisibility(leer ? View.VISIBLE : View.GONE);
            binding.textLeerTitel.setText(viewModel.hatBestand()
                    ? R.string.bestand_kein_treffer_titel : R.string.bestand_leer_titel);
            binding.textLeerText.setText(viewModel.hatBestand()
                    ? R.string.bestand_kein_treffer_text : R.string.bestand_leer_text);
            binding.textAnzahl.setText(getResources().getQuantityString(
                    R.plurals.bestand_anzahl, artikel == null ? 0 : artikel.size(),
                    artikel == null ? 0 : artikel.size()));
        });

        viewModel.getAlle().observe(getViewLifecycleOwner(), artikel -> {
            aktuelleListe.clear();
            if (artikel != null) {
                aktuelleListe.addAll(artikel);
            }
            zahlenZeichnen(aktuelleListe);
            bannerZeichnen(aktuelleListe);
            auswahllistenFuellen();
        });

        binding.buttonAuswahl.setVisibility(
                Einstellungen.darfBearbeiten(requireContext()) ? View.VISIBLE : View.GONE);
    }

    /** Ziehen zum Abgleichen - ohne Server nur ein Hinweis. */
    private void abgleichen() {
        if (!Einstellungen.serverAktiv(requireContext())
                || Einstellungen.teamId(requireContext()) == null) {
            binding.swipeRefresh.setRefreshing(false);
            Toast.makeText(requireContext(), R.string.sync_kein_server, Toast.LENGTH_SHORT).show();
            return;
        }
        Abgleich.ausfuehren(requireContext(), ergebnis -> {
            if (binding == null) {
                return;
            }
            binding.swipeRefresh.setRefreshing(false);
            if (!ergebnis.erfolgreich()) {
                // Ohne Verbindung ist nichts verloren: die Aenderungen sind vorgemerkt.
                Toast.makeText(requireContext(), ergebnis.laeuftSchon
                        ? getString(R.string.server_abgleich_laeuft)
                        : ergebnis.fehler + "\n"
                        + getString(R.string.server_uebertragung_geplant),
                        Toast.LENGTH_LONG).show();
                return;
            }
            String meldung = getString(R.string.sync_fertig,
                    ergebnis.hochgeladen, ergebnis.uebernommen);
            if (!ergebnis.gruende.isEmpty()) {
                // Meist eine doppelt vergebene Kennung - das gehoert vor Augen.
                meldung += "\n" + getString(R.string.sync_abgelehnt_hinweis,
                        ergebnis.abgelehnt, ergebnis.gruende.get(0));
            }
            Toast.makeText(requireContext(), meldung,
                    ergebnis.gruende.isEmpty() ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        });
    }

    // ------------------------------------------------------------------ Filter

    private void statusFilter(@Nullable ArtikelStatus status) {
        viewModel.setStatus(viewModel.getStatus() == status ? null : status);
        zahlenZeichnen(aktuelleListe);
        filterChipsZeichnen();
    }

    private void auswahllistenFuellen() {
        List<String> kategorien = new ArrayList<>();
        kategorien.add(getString(R.string.filter_alle_kategorien));
        kategorien.addAll(viewModel.verwendeteKategorien());
        binding.dropdownKategorie.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, kategorien));
        binding.dropdownKategorie.setOnItemClickListener((parent, view, position, id) -> {
            viewModel.setKategorie(position == 0 ? BestandViewModel.ALLE : kategorien.get(position));
            filterChipsZeichnen();
        });

        List<String> standorte = new ArrayList<>();
        standorte.add(getString(R.string.filter_alle_standorte));
        standorte.addAll(viewModel.verwendeteStandorte());
        binding.dropdownStandort.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, standorte));
        binding.dropdownStandort.setOnItemClickListener((parent, view, position, id) -> {
            viewModel.setStandort(position == 0 ? BestandViewModel.ALLE : standorte.get(position));
            filterChipsZeichnen();
        });
    }

    private void filterChipsZeichnen() {
        binding.chipGroupFilter.removeAllViews();
        if (viewModel.getStatus() != null) {
            chip(Formatter.statusLabel(requireContext(), viewModel.getStatus()), () -> {
                viewModel.setStatus(null);
                zahlenZeichnen(aktuelleListe);
                filterChipsZeichnen();
            });
        }
        if (!BestandViewModel.ALLE.equals(viewModel.getKategorie())) {
            chip(viewModel.getKategorie(), () -> {
                viewModel.setKategorie(BestandViewModel.ALLE);
                binding.dropdownKategorie.setText(getString(R.string.filter_alle_kategorien), false);
                filterChipsZeichnen();
            });
        }
        if (!BestandViewModel.ALLE.equals(viewModel.getStandort())) {
            chip(viewModel.getStandort(), () -> {
                viewModel.setStandort(BestandViewModel.ALLE);
                binding.dropdownStandort.setText(getString(R.string.filter_alle_standorte), false);
                filterChipsZeichnen();
            });
        }
        binding.chipGroupFilter.setVisibility(
                binding.chipGroupFilter.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void chip(String text, Runnable entfernen) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> entfernen.run());
        chip.setOnClickListener(v -> entfernen.run());
        binding.chipGroupFilter.addView(chip);
    }

    // ------------------------------------------------------------------ Zahlen

    private void zahlenZeichnen(List<Artikel> artikel) {
        int vorhanden = 0;
        int fehlt = 0;
        int verliehen = 0;
        for (Artikel eintrag : artikel) {
            if (eintrag.status == ArtikelStatus.VORHANDEN) {
                vorhanden++;
            } else if (eintrag.status == ArtikelStatus.NICHT_VORHANDEN) {
                fehlt++;
            } else if (eintrag.status == ArtikelStatus.VERLIEHEN) {
                verliehen++;
            }
        }
        binding.textGesamt.setText(String.valueOf(artikel.size()));
        binding.textVorhanden.setText(String.valueOf(vorhanden));
        binding.textFehlt.setText(String.valueOf(fehlt));
        binding.textVerliehen.setText(String.valueOf(verliehen));

        ArtikelStatus aktiv = viewModel.getStatus();
        binding.cardGesamt.setChecked(aktiv == null);
        binding.cardVorhanden.setChecked(aktiv == ArtikelStatus.VORHANDEN);
        binding.cardFehlt.setChecked(aktiv == ArtikelStatus.NICHT_VORHANDEN);
        binding.cardVerliehen.setChecked(aktiv == ArtikelStatus.VERLIEHEN);
    }

    // ----------------------------------------------------------------- Banner

    private void bannerZeichnen(List<Artikel> artikel) {
        List<Artikel> ueberfaellig = new ArrayList<>();
        List<Artikel> vermisst = new ArrayList<>();
        for (Artikel eintrag : artikel) {
            if (eintrag.istUeberfaellig()) {
                ueberfaellig.add(eintrag);
            }
            if (eintrag.istVermisst()) {
                vermisst.add(eintrag);
            }
        }

        boolean zeigeUeberfaellig = !ueberfaellig.isEmpty() && !ueberfaelligVerborgen;
        binding.cardUeberfaellig.setVisibility(zeigeUeberfaellig ? View.VISIBLE : View.GONE);
        if (zeigeUeberfaellig) {
            binding.textUeberfaelligTitel.setText(getResources().getQuantityString(
                    R.plurals.banner_ueberfaellig, ueberfaellig.size(), ueberfaellig.size()));
            binding.containerUeberfaellig.setVisibility(ueberfaelligOffen ? View.VISIBLE : View.GONE);
            binding.containerUeberfaellig.removeAllViews();
            if (ueberfaelligOffen) {
                for (Artikel eintrag : ueberfaellig) {
                    binding.containerUeberfaellig.addView(bannerZeile(eintrag,
                            getString(R.string.banner_ueberfaellig_zeile,
                                    eintrag.verliehenAn == null ? "?" : eintrag.verliehenAn,
                                    Formatter.datum(requireContext(), eintrag.rueckgabeDatum)),
                            getString(R.string.banner_tage, Formatter.tageUeberfaellig(eintrag))));
                }
            }
        }

        binding.cardVermisst.setVisibility(vermisst.isEmpty() ? View.GONE : View.VISIBLE);
        if (!vermisst.isEmpty()) {
            binding.textVermisstTitel.setText(getResources().getQuantityString(
                    R.plurals.banner_vermisst, vermisst.size(), vermisst.size()));
            binding.containerVermisst.setVisibility(vermisstOffen ? View.VISIBLE : View.GONE);
            binding.containerVermisst.removeAllViews();
            if (vermisstOffen) {
                for (Artikel eintrag : vermisst) {
                    String wann = eintrag.zuletztGescannt == null
                            ? getString(R.string.banner_nie_gescannt)
                            : getString(R.string.banner_zuletzt,
                            Formatter.seit(eintrag.zuletztGescannt));
                    binding.containerVermisst.addView(bannerZeile(eintrag, wann, null));
                }
            }
        }
    }

    private View bannerZeile(Artikel artikel, String zeile, @Nullable String rechts) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_banner_zeile, binding.containerVermisst, false);
        ((TextView) view.findViewById(R.id.textName)).setText(artikel.name);
        ((TextView) view.findViewById(R.id.textZeile)).setText(zeile);
        TextView rechtsText = view.findViewById(R.id.textRechts);
        rechtsText.setVisibility(rechts == null ? View.GONE : View.VISIBLE);
        rechtsText.setText(rechts);
        view.setOnClickListener(v -> oeffnen(artikel));
        return view;
    }

    // ------------------------------------------------------------------ Auswahl

    private void auswahlUmschalten() {
        boolean an = !adapter.istAuswahlModus();
        adapter.setAuswahlModus(an);
        binding.buttonAuswahl.setText(an ? R.string.action_cancel : R.string.bestand_auswaehlen);
        aktionsleisteAktualisieren();
    }

    private void auswahlBeenden() {
        adapter.setAuswahlModus(false);
        binding.buttonAuswahl.setText(R.string.bestand_auswaehlen);
        aktionsleisteAktualisieren();
    }

    private void aktionsleisteAktualisieren() {
        int anzahl = adapter.getAusgewaehlt().size();
        binding.cardAktionen.setVisibility(anzahl > 0 ? View.VISIBLE : View.GONE);
        binding.textAuswahlAnzahl.setText(getString(R.string.bestand_ausgewaehlt, anzahl));
    }

    private List<Artikel> ausgewaehlteArtikel() {
        List<Artikel> auswahl = new ArrayList<>();
        for (Artikel artikel : aktuelleListe) {
            if (adapter.getAusgewaehlt().contains(artikel.id)) {
                auswahl.add(artikel);
            }
        }
        return auswahl;
    }

    private void statusFuerAuswahl() {
        ArtikelStatus[] werte = ArtikelStatus.values();
        String[] namen = new String[werte.length];
        for (int i = 0; i < werte.length; i++) {
            namen[i] = getString(werte[i].labelRes);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bestand_status_setzen)
                .setItems(namen, (dialog, welcher) -> {
                    List<Long> ids = new ArrayList<>(adapter.getAusgewaehlt());
                    repository.mehrfachStatus(ids, werte[welcher],
                            Einstellungen.nutzer(requireContext()), anzahl -> {
                                Toast.makeText(requireContext(),
                                        getString(R.string.bestand_status_gesetzt, anzahl,
                                                getString(werte[welcher].labelRes)),
                                        Toast.LENGTH_SHORT).show();
                                auswahlBeenden();
                            });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void standortFuerAuswahl() {
        List<String> standorte = viewModel.verwendeteStandorte();
        List<String> eintraege = new ArrayList<>(standorte);
        eintraege.add(getString(R.string.bestand_standort_neu));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.bestand_standort_setzen)
                .setItems(eintraege.toArray(new String[0]), (dialog, welcher) -> {
                    if (welcher == standorte.size()) {
                        de.tagstock.util.Dialogs.textInput(requireContext(),
                                getString(R.string.bestand_standort_neu),
                                getString(R.string.artikel_standort), null,
                                this::standortSetzen);
                    } else {
                        standortSetzen(standorte.get(welcher));
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void standortSetzen(String standort) {
        List<Long> ids = new ArrayList<>(adapter.getAusgewaehlt());
        repository.mehrfachStandort(ids, standort, Einstellungen.nutzer(requireContext()),
                anzahl -> {
                    Toast.makeText(requireContext(),
                            getString(R.string.bestand_standort_gesetzt, anzahl, standort),
                            Toast.LENGTH_SHORT).show();
                    auswahlBeenden();
                });
    }

    private void etikettenDrucken() {
        etikettenAuswahl = ausgewaehlteArtikel();
        if (etikettenAuswahl.isEmpty()) {
            return;
        }
        etikettenLauncher.launch(Sicherung.dateiname("pdf").replace(".pdf", "-etiketten.pdf"));
    }

    private void etikettenSchreiben(@Nullable Uri ziel) {
        if (ziel == null || etikettenAuswahl.isEmpty()) {
            return;
        }
        List<Artikel> auswahl = new ArrayList<>(etikettenAuswahl);
        Hintergrund.starte(() -> {
            try (OutputStream out = requireContext().getContentResolver()
                    .openOutputStream(ziel, "wt")) {
                PdfErzeuger.etiketten(requireContext(), auswahl, out);
            }
            return auswahl.size();
        }, anzahl -> {
            Toast.makeText(requireContext(), getString(R.string.etiketten_fertig, anzahl),
                    Toast.LENGTH_LONG).show();
            auswahlBeenden();
        }, fehler -> Toast.makeText(requireContext(),
                getString(R.string.etiketten_fehler, String.valueOf(fehler.getMessage())),
                Toast.LENGTH_LONG).show());
    }

    // ------------------------------------------------------------------ Klicks

    @Override
    public void onArtikelClick(Artikel artikel) {
        if (adapter.istAuswahlModus()) {
            adapter.umschalten(artikel);
            aktionsleisteAktualisieren();
            return;
        }
        oeffnen(artikel);
    }

    @Override
    public void onArtikelLongClick(Artikel artikel) {
        if (!Einstellungen.darfBearbeiten(requireContext())) {
            return;
        }
        if (!adapter.istAuswahlModus()) {
            adapter.setAuswahlModus(true);
            binding.buttonAuswahl.setText(R.string.action_cancel);
        }
        adapter.umschalten(artikel);
        aktionsleisteAktualisieren();
    }

    private void oeffnen(Artikel artikel) {
        startActivity(ArtikelDetailActivity.intent(requireContext(), artikel.id));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
