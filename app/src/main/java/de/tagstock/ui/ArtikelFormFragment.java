package de.tagstock.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import de.tagstock.R;
import de.tagstock.data.Abgleich;
import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.Kategorie;
import de.tagstock.data.Repository;
import de.tagstock.data.ScanWarnung;
import de.tagstock.databinding.FragmentArtikelFormBinding;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.Formatter;
import de.tagstock.util.FotoLader;
import de.tagstock.util.Fotos;
import de.tagstock.util.Gtin;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.ScanResult;
import de.tagstock.util.ServerClient;

/** Formular zum Anlegen und Bearbeiten eines Artikels. */
public class ArtikelFormFragment extends Fragment {

    /** Wird nach dem Speichern aufgerufen, wenn das Formular eingebettet ist. */
    public interface Abschluss {
        void onGespeichert(long artikelId);

        void onAbgebrochen();
    }

    private static final String ARG_ID = "artikelId";
    private static final String ARG_KENNUNG = "kennung";

    private FragmentArtikelFormBinding binding;
    private Repository repository;

    private long artikelId;
    @Nullable
    private Artikel original;
    private String fotoPfad;
    private String bildUrl;
    private String aufnahmeName;
    private final List<String> neueFotos = new ArrayList<>();
    private Long rueckgabeDatum;
    private final List<String> kategorien = new ArrayList<>();

    /** Die Kennung wird waehrend der Eingabe geprueft - verzoegert, nicht bei jedem Zeichen. */
    private final android.os.Handler pruefer = new android.os.Handler(Looper.getMainLooper());
    private final Runnable kennungPruefen = this::kennungPruefen;
    @Nullable
    private Artikel kennungGehoertZu;
    @Nullable
    private String vorschlagName;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), ergebnis -> {
                if (ergebnis.getResultCode() != android.app.Activity.RESULT_OK) {
                    return;
                }
                ScanResult scan = ScanResult.fromIntent(ergebnis.getData());
                if (scan != null) {
                    binding.editKennung.setText(scan.code);
                }
            });

    private final ActivityResultLauncher<Uri> kameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), erfolg -> {
                if (erfolg != null && erfolg && aufnahmeName != null) {
                    fotoUebernehmen(aufnahmeName);
                } else {
                    Fotos.loeschen(requireContext(), aufnahmeName);
                }
                aufnahmeName = null;
            });

    private final ActivityResultLauncher<String> galerieLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                String name = Fotos.uebernehmen(requireContext(), uri);
                if (name == null) {
                    Toast.makeText(requireContext(), R.string.foto_fehler, Toast.LENGTH_SHORT).show();
                } else {
                    fotoUebernehmen(name);
                }
            });

    public static ArtikelFormFragment neu() {
        return new ArtikelFormFragment();
    }

    public static ArtikelFormFragment bearbeiten(long artikelId) {
        ArtikelFormFragment fragment = new ArtikelFormFragment();
        Bundle argumente = new Bundle();
        argumente.putLong(ARG_ID, artikelId);
        fragment.setArguments(argumente);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentArtikelFormBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = Repository.getInstance(requireContext());
        artikelId = getArguments() == null ? 0L : getArguments().getLong(ARG_ID, 0L);

        binding.buttonFotoKamera.setOnClickListener(v -> fotoAufnehmen());
        binding.buttonFotoGalerie.setOnClickListener(v -> galerieLauncher.launch("image/*"));
        binding.buttonFotoEntfernen.setOnClickListener(v -> {
            fotoPfad = null;
            bildUrl = null;
            fotoAnzeigen();
        });
        binding.buttonKennungScannen.setOnClickListener(v ->
                scanLauncher.launch(ScannerActivity.intent(requireContext())));
        binding.editKennung.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int anzahl, int nach) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int vorher, int anzahl) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                pruefer.removeCallbacks(kennungPruefen);
                pruefer.postDelayed(kennungPruefen, 350L);
            }
        });
        binding.buttonKennungOeffnen.setOnClickListener(v -> {
            if (kennungGehoertZu != null) {
                startActivity(ArtikelDetailActivity.intent(requireContext(),
                        kennungGehoertZu.id));
            }
        });
        binding.buttonVorschlagUebernehmen.setOnClickListener(v -> {
            if (vorschlagName != null) {
                binding.editName.setText(vorschlagName);
                binding.gruppeVorschlag.setVisibility(View.GONE);
            }
        });
        binding.buttonSpeichern.setOnClickListener(v -> speichern());
        binding.editRueckgabe.setOnClickListener(v -> datumWaehlen());
        binding.inputRueckgabe.setEndIconOnClickListener(v -> {
            rueckgabeDatum = null;
            binding.editRueckgabe.setText("");
        });

        statusAuswahlFuellen();
        scanWarnungFuellen();
        kategorienLaden();

        if (artikelId != 0L) {
            binding.buttonSpeichern.setText(R.string.action_save);
            repository.ladeArtikel(artikelId, geladen -> {
                if (geladen == null) {
                    return;
                }
                original = geladen;
                fuellen(geladen);
            });
        } else {
            binding.buttonSpeichern.setText(R.string.artikel_anlegen);
            statusSetzen(ArtikelStatus.VORHANDEN);
            warnungSetzen(ScanWarnung.JAHR);
            String kennung = getArguments() == null ? null : getArguments().getString(ARG_KENNUNG);
            if (kennung != null) {
                binding.editKennung.setText(kennung);
            }
        }
        verleihFelder();
        fotoAnzeigen();
    }

    /** Traegt eine gescannte Kennung nachtraeglich ein. */
    public void kennungSetzen(String kennung) {
        if (binding == null) {
            Bundle argumente = getArguments() == null ? new Bundle() : getArguments();
            argumente.putString(ARG_KENNUNG, kennung);
            setArguments(argumente);
            return;
        }
        binding.editKennung.setText(kennung);
    }

    // ------------------------------------------------------------------ Kennung

    /**
     * Prueft die eingegebene Kennung: Ist sie schon vergeben, steht hier, zu
     * welchem Artikel sie gehoert. Sieht sie nach einer Handelsnummer aus,
     * wird die Pruefziffer nachgerechnet und - falls eingeschaltet - der Name
     * beim Server nachgeschlagen.
     */
    private void kennungPruefen() {
        if (binding == null) {
            return;
        }
        String kennung = text(binding.editKennung.getText());
        kennungGehoertZu = null;
        binding.buttonKennungOeffnen.setVisibility(View.GONE);
        binding.gruppeVorschlag.setVisibility(View.GONE);
        vorschlagName = null;

        if (kennung.isEmpty()) {
            binding.textKennungHinweis.setVisibility(View.GONE);
            return;
        }

        hinweisZurNummer(kennung);

        repository.findeNachKennung(java.util.Collections.singletonList(kennung), treffer -> {
            if (binding == null) {
                return;
            }
            boolean fremd = treffer != null && treffer.id != artikelId;
            kennungGehoertZu = fremd ? treffer : null;
            binding.buttonKennungOeffnen.setVisibility(fremd ? View.VISIBLE : View.GONE);
            if (fremd) {
                binding.textKennungHinweis.setVisibility(View.VISIBLE);
                String wo = Formatter.zeile(requireContext(), treffer);
                binding.textKennungHinweis.setText(wo.isEmpty()
                        ? getString(R.string.kennung_belegt_kurz, treffer.name)
                        : getString(R.string.kennung_belegt_kurz_wo, treffer.name, wo));
                binding.textKennungHinweis.setTextColor(hinweisFarbe(true));
                return;
            }
            hinweisZurNummer(kennung);
            nachschlagen(kennung);
        });
    }

    /** Zeigt Art und Herkunft der Nummer, oder dass die Pruefziffer nicht stimmt. */
    private void hinweisZurNummer(String kennung) {
        String art = Gtin.art(kennung);
        if (art == null) {
            binding.textKennungHinweis.setVisibility(View.GONE);
            return;
        }
        String text;
        boolean fehler = !Gtin.pruefzifferStimmt(kennung);
        if (fehler) {
            text = getString(R.string.kennung_pruefziffer_falsch, art);
        } else {
            String herkunft = Gtin.herkunft(kennung);
            text = herkunft == null ? art : art + " · " + herkunft;
        }
        binding.textKennungHinweis.setVisibility(View.VISIBLE);
        binding.textKennungHinweis.setText(text);
        binding.textKennungHinweis.setTextColor(hinweisFarbe(fehler));
    }

    /** Rot fuer Warnungen, sonst die gedaempfte Schriftfarbe des Hinweises. */
    private int hinweisFarbe(boolean warnung) {
        return warnung
                ? androidx.core.content.ContextCompat.getColor(requireContext(),
                        R.color.status_fehlt)
                : binding.textVorschlag.getCurrentTextColor();
    }

    /** Fragt den Server nach dem Produktnamen - nur wenn das Feld noch leer ist. */
    private void nachschlagen(String kennung) {
        if (!Einstellungen.gtinNachschlagen(requireContext())
                || !Einstellungen.serverAktiv(requireContext())
                || !Gtin.pruefzifferStimmt(kennung) || Gtin.intern(kennung)
                || !text(binding.editName.getText()).isEmpty()) {
            return;
        }
        String url = Einstellungen.serverUrl(requireContext());
        String token = Einstellungen.token(requireContext());
        if (url == null || token == null) {
            return;
        }
        Hintergrund.starte(() -> new ServerClient(url, token).gtin(kennung), antwort -> {
            if (binding == null || antwort == null || !antwort.optBoolean("gefunden")) {
                return;
            }
            String name = antwort.optString("name", "");
            if (name.isEmpty() || !kennung.equals(text(binding.editKennung.getText()))
                    || !text(binding.editName.getText()).isEmpty()) {
                return;
            }
            vorschlagName = name;
            binding.textVorschlag.setText(getString(R.string.vorschlag_text, name));
            binding.gruppeVorschlag.setVisibility(View.VISIBLE);
        }, fehler -> {
            // Kein Treffer, kein Dienst, kein Netz - dann eben ohne Vorschlag.
        });
    }

    // ---------------------------------------------------------------- Bestueckung

    private void statusAuswahlFuellen() {
        ArtikelStatus[] werte = ArtikelStatus.values();
        List<String> namen = new ArrayList<>();
        for (ArtikelStatus status : werte) {
            namen.add(getString(status.labelRes));
        }
        binding.dropdownStatus.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, namen));
        binding.dropdownStatus.setOnItemClickListener((parent, view, position, id) -> {
            binding.dropdownStatus.setTag(werte[position]);
            verleihFelder();
        });
    }

    private void scanWarnungFuellen() {
        ScanWarnung[] werte = ScanWarnung.values();
        List<String> namen = new ArrayList<>();
        for (ScanWarnung warnung : werte) {
            namen.add(getString(warnung.labelRes));
        }
        binding.dropdownWarnung.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, namen));
        binding.dropdownWarnung.setOnItemClickListener((parent, view, position, id) ->
                binding.dropdownWarnung.setTag(werte[position]));
    }

    private void kategorienLaden() {
        repository.ladeKategorien(liste -> {
            kategorien.clear();
            for (Kategorie kategorie : liste) {
                kategorien.add(kategorie.name);
            }
            binding.dropdownKategorie.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, kategorien));
        });
    }

    private void fuellen(Artikel artikel) {
        binding.editName.setText(artikel.name);
        binding.editBeschreibung.setText(artikel.beschreibung);
        binding.dropdownKategorie.setText(artikel.kategorie, false);
        binding.editStandort.setText(artikel.standort);
        binding.editLagerort.setText(artikel.lagerort);
        binding.editKennung.setText(artikel.rfidUid);
        binding.editVerliehenAn.setText(artikel.verliehenAn);
        rueckgabeDatum = artikel.rueckgabeDatum;
        binding.editRueckgabe.setText(rueckgabeDatum == null
                ? "" : Formatter.datum(requireContext(), rueckgabeDatum));
        fotoPfad = artikel.fotoPfad;
        bildUrl = artikel.bildUrl;
        statusSetzen(artikel.status);
        warnungSetzen(artikel.scanWarnung);
        verleihFelder();
        fotoAnzeigen();
    }

    private void statusSetzen(ArtikelStatus status) {
        binding.dropdownStatus.setTag(status);
        binding.dropdownStatus.setText(getString(status.labelRes), false);
    }

    private void warnungSetzen(ScanWarnung warnung) {
        binding.dropdownWarnung.setTag(warnung);
        binding.dropdownWarnung.setText(getString(warnung.labelRes), false);
    }

    private ArtikelStatus status() {
        Object tag = binding.dropdownStatus.getTag();
        return tag instanceof ArtikelStatus ? (ArtikelStatus) tag : ArtikelStatus.VORHANDEN;
    }

    private ScanWarnung warnung() {
        Object tag = binding.dropdownWarnung.getTag();
        return tag instanceof ScanWarnung ? (ScanWarnung) tag : ScanWarnung.JAHR;
    }

    private void verleihFelder() {
        boolean verliehen = status() == ArtikelStatus.VERLIEHEN;
        binding.inputVerliehenAn.setVisibility(verliehen ? View.VISIBLE : View.GONE);
        binding.inputRueckgabe.setVisibility(verliehen ? View.VISIBLE : View.GONE);
    }

    private void datumWaehlen() {
        Calendar kalender = Calendar.getInstance();
        if (rueckgabeDatum != null) {
            kalender.setTimeInMillis(rueckgabeDatum);
        }
        new DatePickerDialog(requireContext(), (view, jahr, monat, tag) -> {
            Calendar gewaehlt = Calendar.getInstance();
            gewaehlt.set(jahr, monat, tag, 0, 0, 0);
            rueckgabeDatum = Formatter.tagesbeginn(gewaehlt.getTimeInMillis());
            binding.editRueckgabe.setText(Formatter.datum(requireContext(), rueckgabeDatum));
        }, kalender.get(Calendar.YEAR), kalender.get(Calendar.MONTH),
                kalender.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ------------------------------------------------------------------- Foto

    private void fotoAufnehmen() {
        try {
            aufnahmeName = Fotos.neuerName();
            kameraLauncher.launch(Fotos.uriFuer(requireContext(), aufnahmeName));
        } catch (IOException e) {
            aufnahmeName = null;
            Toast.makeText(requireContext(), R.string.foto_fehler, Toast.LENGTH_SHORT).show();
        }
    }

    private void fotoUebernehmen(String name) {
        neueFotos.add(name);
        fotoPfad = name;
        // Eine neue Aufnahme ersetzt das Bild auf dem Server beim naechsten Abgleich.
        bildUrl = null;
        FotoLader.vergessen(name);
        fotoAnzeigen();
    }

    private void fotoAnzeigen() {
        boolean aufDemGeraet = Fotos.existiert(requireContext(), fotoPfad);
        boolean aufDemServer = bildUrl != null && !bildUrl.isEmpty();
        FotoLader.laden(binding.imageFoto, aufDemGeraet ? fotoPfad : null,
                aufDemServer ? bildUrl : null, R.drawable.ic_artikel);
        binding.buttonFotoEntfernen.setVisibility(
                aufDemGeraet || aufDemServer ? View.VISIBLE : View.GONE);
        binding.textFotoHinweis.setVisibility(
                aufDemGeraet && Einstellungen.serverAktiv(requireContext())
                        ? View.VISIBLE : View.GONE);
    }

    // --------------------------------------------------------------- Speichern

    private void speichern() {
        String name = text(binding.editName.getText());
        if (name.isEmpty()) {
            binding.editName.setError(getString(R.string.artikel_name_fehlt));
            binding.editName.requestFocus();
            return;
        }

        Artikel ziel = original == null ? new Artikel() : original.kopie();
        ziel.name = name;
        ziel.beschreibung = Formatter.leerZuNull(text(binding.editBeschreibung.getText()));
        ziel.kategorie = Formatter.leerZuNull(text(binding.dropdownKategorie.getText()));
        ziel.standort = Formatter.leerZuNull(text(binding.editStandort.getText()));
        ziel.lagerort = Formatter.leerZuNull(text(binding.editLagerort.getText()));
        ziel.rfidUid = Formatter.leerZuNull(text(binding.editKennung.getText()));
        ziel.status = status();
        ziel.scanWarnung = warnung();
        ziel.fotoPfad = fotoPfad;
        ziel.bildUrl = bildUrl;
        if (ziel.status == ArtikelStatus.VERLIEHEN) {
            ziel.verliehenAn = Formatter.leerZuNull(text(binding.editVerliehenAn.getText()));
            ziel.rueckgabeDatum = rueckgabeDatum;
        } else {
            ziel.verliehenAn = null;
            ziel.rueckgabeDatum = null;
        }

        if (original != null && original.fotoPfad != null
                && !original.fotoPfad.equals(fotoPfad)) {
            Fotos.loeschen(requireContext(), original.fotoPfad);
        }

        repository.speichern(ziel, original, Einstellungen.nutzer(requireContext()), ergebnis -> {
            if (!ergebnis.erfolgreich) {
                kennungBelegt(ergebnis.kennungBelegtVon);
                return;
            }
            aufraeumen(ziel.fotoPfad);
            Toast.makeText(requireContext(), R.string.artikel_gespeichert,
                    Toast.LENGTH_SHORT).show();
            if (Einstellungen.serverAktiv(requireContext())) {
                // Sofort weiterreichen, damit vor allem das Bild zeitnah auf dem
                // Server landet. Klappt das nicht, ist es schon vorgemerkt.
                Abgleich.ausfuehren(requireContext().getApplicationContext(), abgleich -> {
                    if (binding == null || abgleich.erfolgreich() || abgleich.laeuftSchon) {
                        return;
                    }
                    Toast.makeText(requireContext(), R.string.server_uebertragung_geplant,
                            Toast.LENGTH_SHORT).show();
                });
            }
            if (getActivity() instanceof ArtikelDetailActivity) {
                ((ArtikelDetailActivity) getActivity()).bearbeitenBeenden();
            } else {
                zuruecksetzen();
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).zeigeBestand();
                }
            }
        });
    }

    private void kennungBelegt(@Nullable Artikel belegtVon) {
        if (belegtVon == null) {
            Toast.makeText(requireContext(), R.string.kennung_belegt_unbekannt,
                    Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.kennung_belegt_titel)
                .setMessage(getString(R.string.kennung_belegt_text,
                        text(binding.editKennung.getText()), belegtVon.name))
                .setNegativeButton(R.string.action_ok, null)
                .setPositiveButton(R.string.treffer_oeffnen, (dialog, welcher) ->
                        startActivity(ArtikelDetailActivity.intent(requireContext(), belegtVon.id)))
                .show();
    }

    private void aufraeumen(@Nullable String behalten) {
        for (String name : neueFotos) {
            if (behalten == null || !behalten.equals(name)) {
                Fotos.loeschen(requireContext(), name);
            }
        }
        neueFotos.clear();
    }

    private void zuruecksetzen() {
        original = null;
        artikelId = 0L;
        fotoPfad = null;
        bildUrl = null;
        rueckgabeDatum = null;
        binding.editName.setText("");
        binding.editBeschreibung.setText("");
        binding.dropdownKategorie.setText("", false);
        binding.editStandort.setText("");
        binding.editLagerort.setText("");
        binding.editKennung.setText("");
        binding.editVerliehenAn.setText("");
        binding.editRueckgabe.setText("");
        statusSetzen(ArtikelStatus.VORHANDEN);
        warnungSetzen(ScanWarnung.JAHR);
        verleihFelder();
        fotoAnzeigen();
    }

    /** true, wenn Eingaben gemacht wurden, die noch nicht gespeichert sind. */
    public boolean hatAenderungen() {
        if (binding == null) {
            return false;
        }
        if (original == null) {
            return !text(binding.editName.getText()).isEmpty()
                    || !text(binding.editKennung.getText()).isEmpty()
                    || fotoPfad != null;
        }
        return !text(binding.editName.getText()).equals(original.name)
                || !text(binding.editBeschreibung.getText()).equals(wert(original.beschreibung))
                || !text(binding.editStandort.getText()).equals(wert(original.standort))
                || !text(binding.editLagerort.getText()).equals(wert(original.lagerort))
                || !text(binding.editKennung.getText()).equals(wert(original.rfidUid))
                || status() != original.status;
    }

    private String wert(@Nullable String wert) {
        return wert == null ? "" : wert;
    }

    private String text(CharSequence wert) {
        return wert == null ? "" : wert.toString().trim();
    }

    @Override
    public void onDestroyView() {
        pruefer.removeCallbacks(kennungPruefen);
        binding = null;
        super.onDestroyView();
    }
}
