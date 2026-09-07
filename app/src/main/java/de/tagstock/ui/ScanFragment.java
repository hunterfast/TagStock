package de.tagstock.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.tagstock.R;
import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.Repository;
import de.tagstock.databinding.FragmentScanBinding;
import de.tagstock.databinding.ItemScanLogBinding;
import de.tagstock.util.CodeArt;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.ScanResult;
import de.tagstock.util.ScannerSteuerung;

/**
 * Scan-Bereich mit drei Betriebsarten: einzeln pruefen, sammeln und gemeinsam
 * buchen oder im Dauerlauf sofort als vorhanden markieren.
 */
public class ScanFragment extends Fragment
        implements ScannerSteuerung.Listener, MainActivity.NfcEmpfaenger {

    private enum Modus { EINZEL, LISTE, SCHNELL }

    /** Ein Eintrag im Scan-Protokoll. */
    private static class Eintrag {
        final String code;
        String name;
        long artikelId;
        Art art;

        Eintrag(String code) {
            this.code = code;
        }
    }

    private enum Art { GEBUCHT, BEREITS, UNBEKANNT, OFFEN }

    private FragmentScanBinding binding;
    private Repository repository;
    private ScannerSteuerung scanner;
    private Modus modus = Modus.EINZEL;

    private final List<Eintrag> eintraege = new ArrayList<>();
    private final Set<String> bekannteCodes = new LinkedHashSet<>();
    private LogAdapter logAdapter;

    private final ActivityResultLauncher<String> berechtigung = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), erlaubt -> {
                if (erlaubt) {
                    scanner.kameraStarten();
                } else {
                    onKameraFehlt();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentScanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = Repository.getInstance(requireContext());
        scanner = new ScannerSteuerung(requireContext(), getViewLifecycleOwner(),
                binding.previewView, this);

        logAdapter = new LogAdapter();
        binding.recyclerLog.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerLog.setAdapter(logAdapter);

        binding.buttonModusEinzel.setOnClickListener(v -> modusSetzen(Modus.EINZEL));
        binding.buttonModusListe.setOnClickListener(v -> modusSetzen(Modus.LISTE));
        binding.buttonModusSchnell.setOnClickListener(v -> modusSetzen(Modus.SCHNELL));
        binding.buttonBlitz.setOnClickListener(v -> scanner.blitzUmschalten());
        binding.buttonManuell.setOnClickListener(v -> Dialogs.textInput(requireContext(),
                getString(R.string.scan_manuell_titel), getString(R.string.artikel_kennung), null,
                wert -> verarbeiten(new ScanResult(wert, CodeArt.MANUELL, null))));
        binding.buttonBuchen.setOnClickListener(v -> listeBuchen());
        binding.buttonLeeren.setOnClickListener(v -> {
            eintraege.clear();
            bekannteCodes.clear();
            logAdapter.notifyDataSetChanged();
            zusammenfassung();
        });

        modusSetzen(Modus.EINZEL);

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            scanner.kameraStarten();
        } else {
            berechtigung.launch(Manifest.permission.CAMERA);
        }

        if (NfcHelper.hasHardware(requireContext()) && !NfcHelper.isReady(requireContext())) {
            binding.textHinweis.setText(R.string.scan_nfc_aus);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setNfcEmpfaenger(this);
        }
    }

    @Override
    public void onPause() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setNfcEmpfaenger(null);
        }
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setNfcEmpfaenger(hidden ? null : this);
        }
    }

    @Override
    public void onDestroyView() {
        scanner.freigeben();
        binding = null;
        super.onDestroyView();
    }

    // ------------------------------------------------------------------ Modus

    private void modusSetzen(Modus neu) {
        modus = neu;
        binding.buttonModusEinzel.setChecked(neu == Modus.EINZEL);
        binding.buttonModusListe.setChecked(neu == Modus.LISTE);
        binding.buttonModusSchnell.setChecked(neu == Modus.SCHNELL);

        boolean mitListe = neu != Modus.EINZEL;
        binding.bereichListe.setVisibility(mitListe ? View.VISIBLE : View.GONE);
        binding.buttonBuchen.setVisibility(neu == Modus.LISTE ? View.VISIBLE : View.GONE);
        binding.textHinweis.setText(hinweis(neu));
        scanner.entprellungZuruecksetzen();
    }

    private int hinweis(Modus modus) {
        switch (modus) {
            case LISTE:
                return R.string.scan_hinweis_liste;
            case SCHNELL:
                return R.string.scan_hinweis_schnell;
            default:
                return R.string.scan_hinweis_einzel;
        }
    }

    // ------------------------------------------------------------------ Scans

    @Override
    public void onCode(ScanResult ergebnis) {
        verarbeiten(ergebnis);
    }

    @Override
    public void onNfcCode(ScanResult ergebnis) {
        verarbeiten(ergebnis);
    }

    @Override
    public void onKameraFehlt() {
        if (binding == null) {
            return;
        }
        binding.previewView.setVisibility(View.GONE);
        binding.textKeineKamera.setVisibility(View.VISIBLE);
        binding.buttonBlitz.setVisibility(View.GONE);
        binding.textHinweis.setText(R.string.scan_hinweis_nur_nfc);
    }

    @Override
    public void onKameraBereit() {
        if (binding != null) {
            binding.buttonBlitz.setVisibility(scanner.hatBlitz() ? View.VISIBLE : View.GONE);
        }
    }

    private void verarbeiten(ScanResult ergebnis) {
        if (modus == Modus.EINZEL) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).codeOeffnen(ergebnis);
            }
            return;
        }

        String code = ergebnis.code.trim();
        if (code.isEmpty() || !bekannteCodes.add(code)) {
            return;
        }

        Eintrag eintrag = new Eintrag(code);
        eintraege.add(0, eintrag);
        logAdapter.notifyItemInserted(0);
        binding.recyclerLog.scrollToPosition(0);

        repository.findeNachKennung(ergebnis.werte(), treffer -> {
            if (treffer == null) {
                eintrag.art = Art.UNBEKANNT;
            } else {
                eintrag.name = treffer.name;
                eintrag.artikelId = treffer.id;
                if (modus == Modus.SCHNELL) {
                    buchen(eintrag, treffer);
                    return;
                }
                eintrag.art = treffer.status == ArtikelStatus.VORHANDEN ? Art.BEREITS : Art.OFFEN;
            }
            logAdapter.notifyDataSetChanged();
            zusammenfassung();
        });
    }

    /** Markiert einen Artikel als vorhanden und vermerkt den Scan. */
    private void buchen(Eintrag eintrag, Artikel treffer) {
        boolean warVorhanden = treffer.status == ArtikelStatus.VORHANDEN;
        repository.alsGescanntBuchen(treffer.id, Einstellungen.nutzer(requireContext()),
                aktualisiert -> {
                    eintrag.art = warVorhanden ? Art.BEREITS : Art.GEBUCHT;
                    logAdapter.notifyDataSetChanged();
                    zusammenfassung();
                });
    }

    private void listeBuchen() {
        List<Eintrag> offene = new ArrayList<>();
        for (Eintrag eintrag : eintraege) {
            if (eintrag.art == Art.OFFEN) {
                offene.add(eintrag);
            }
        }
        if (offene.isEmpty()) {
            Toast.makeText(requireContext(), R.string.scan_nichts_zu_buchen,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final int[] rest = {offene.size()};
        for (Eintrag eintrag : offene) {
            repository.alsGescanntBuchen(eintrag.artikelId, Einstellungen.nutzer(requireContext()),
                    aktualisiert -> {
                        eintrag.art = Art.GEBUCHT;
                        if (--rest[0] <= 0) {
                            logAdapter.notifyDataSetChanged();
                            zusammenfassung();
                            Toast.makeText(requireContext(),
                                    getString(R.string.scan_gebucht, offene.size()),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void zusammenfassung() {
        int gebucht = 0;
        int bereits = 0;
        int unbekannt = 0;
        for (Eintrag eintrag : eintraege) {
            if (eintrag.art == Art.GEBUCHT) {
                gebucht++;
            } else if (eintrag.art == Art.BEREITS) {
                bereits++;
            } else if (eintrag.art == Art.UNBEKANNT) {
                unbekannt++;
            }
        }
        binding.textZaehlerGebucht.setText(String.valueOf(gebucht));
        binding.textZaehlerBereits.setText(String.valueOf(bereits));
        binding.textZaehlerUnbekannt.setText(String.valueOf(unbekannt));
        binding.textListeLeer.setVisibility(eintraege.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ Liste

    private class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogHolder> {

        @NonNull
        @Override
        public LogHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new LogHolder(ItemScanLogBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull LogHolder holder, int position) {
            holder.bind(eintraege.get(position));
        }

        @Override
        public int getItemCount() {
            return eintraege.size();
        }

        class LogHolder extends RecyclerView.ViewHolder {
            private final ItemScanLogBinding binding;

            LogHolder(ItemScanLogBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            void bind(Eintrag eintrag) {
                binding.textName.setText(eintrag.name == null ? eintrag.code : eintrag.name);
                binding.textCode.setVisibility(eintrag.name == null ? View.GONE : View.VISIBLE);
                binding.textCode.setText(eintrag.code);

                int farbe;
                int label;
                if (eintrag.art == Art.GEBUCHT) {
                    farbe = R.color.status_vorhanden;
                    label = R.string.scan_log_gebucht;
                } else if (eintrag.art == Art.BEREITS) {
                    farbe = R.color.status_verliehen;
                    label = R.string.scan_log_bereits;
                } else if (eintrag.art == Art.UNBEKANNT) {
                    farbe = R.color.brand_primary;
                    label = R.string.scan_log_unbekannt;
                } else {
                    farbe = R.color.status_ausgelagert;
                    label = R.string.scan_log_offen;
                }
                binding.viewPunkt.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), farbe));
                binding.textLabel.setText(label);

                binding.buttonAktion.setVisibility(
                        eintrag.art == Art.UNBEKANNT ? View.VISIBLE : View.GONE);
                binding.buttonAktion.setOnClickListener(v -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).neuMitKennung(eintrag.code);
                    }
                });
                binding.getRoot().setOnClickListener(v -> {
                    if (eintrag.artikelId != 0L) {
                        startActivity(ArtikelDetailActivity.intent(requireContext(),
                                eintrag.artikelId));
                    }
                });
            }
        }
    }
}
