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
import de.tagstock.data.Behaelterkette;
import de.tagstock.data.Packungen;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.Kategorie;
import de.tagstock.data.Repository;
import de.tagstock.data.ScanWarnung;
import de.tagstock.databinding.FragmentArtikelFormBinding;
import de.tagstock.databinding.ZeileOffenePackungBinding;
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

    /** Behaelter, in die dieser Artikel darf - Reihenfolge wie im Auswahlfeld. */
    private final List<Artikel> behaelter = new ArrayList<>();
    @Nullable
    private String liegtIn;

    /** Reste der angebrochenen Packungen, wie sie gerade im Formular stehen. */
    private final List<Integer> offenePackungen = new ArrayList<>();

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

    /** Denselben Scanner, aber der Code gehoert zum Behaelter, nicht zum Artikel. */
    private final ActivityResultLauncher<Intent> behaelterScan = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), ergebnis -> {
                if (ergebnis.getResultCode() != android.app.Activity.RESULT_OK) {
                    return;
                }
                ScanResult scan = ScanResult.fromIntent(ergebnis.getData());
                if (scan != null) {
                    behaelterUebernehmen(scan.code);
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
        binding.buttonBehaelterScannen.setOnClickListener(v ->
                behaelterScan.launch(ScannerActivity.intent(requireContext())));
        binding.schalterBehaelter.setOnCheckedChangeListener((knopf, an) -> behaelterFelder());
        behaelterArtenLaden();

        binding.schalterVerpackung.setOnCheckedChangeListener((knopf, an) -> mengenFelder());
        binding.buttonAnbrechen.setOnClickListener(v -> packungAnbrechen());
        zahlBeobachten(binding.editMenge);
        zahlBeobachten(binding.editPackungsGroesse);
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
                // Ohne Kennung kein Behaelter - der Hinweis haengt am Feld.
                behaelterFelder();
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
            binding.editMenge.setText("1");
            mengenFelder();
            behaelterFelder();
            behaelterLaden();
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
                String wo = Formatter.zeile(requireContext(), treffer, null);
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
    /**
     * Zu einer Handelsnummer den Produktnamen erfragen. Scheitert das, bleibt
     * es nicht still: Wer eine EAN scannt und nichts passieren sieht, soll am
     * Feld lesen koennen, woran es liegt - am ausgeschalteten Schalter, am
     * Server ohne Nachschlagedienst oder daran, dass die Nummer unbekannt ist.
     */
    private void nachschlagen(String kennung) {
        if (!Gtin.pruefzifferStimmt(kennung) || Gtin.intern(kennung)) {
            return;
        }
        if (!text(binding.editName.getText()).isEmpty()) {
            // Es steht schon ein Name da - dann waere ein Vorschlag im Weg.
            return;
        }
        if (!Einstellungen.gtinNachschlagen(requireContext())) {
            nachschlagHinweis(getString(R.string.nachschlagen_aus));
            return;
        }
        String url = Einstellungen.serverUrl(requireContext());
        String token = Einstellungen.token(requireContext());
        if (!Einstellungen.serverAktiv(requireContext()) || url == null || token == null) {
            nachschlagHinweis(getString(R.string.nachschlagen_ohne_server));
            return;
        }
        Hintergrund.starte(() -> new ServerClient(url, token).gtin(kennung), antwort -> {
            if (binding == null || !kennung.equals(text(binding.editKennung.getText()))
                    || !text(binding.editName.getText()).isEmpty()) {
                return;
            }
            String name = antwort == null ? "" : antwort.optString("name", "");
            if (!antwort.optBoolean("gefunden") || name.isEmpty()) {
                nachschlagHinweis(getString(R.string.nachschlagen_unbekannt));
                return;
            }
            vorschlagName = name;
            binding.textVorschlag.setText(getString(R.string.vorschlag_text, name));
            binding.gruppeVorschlag.setVisibility(View.VISIBLE);
        }, fehler -> {
            if (binding == null || !kennung.equals(text(binding.editKennung.getText()))) {
                return;
            }
            // Der Server sagt, warum - meist "kein Nachschlagedienst eingerichtet".
            String meldung = fehler.getMessage();
            nachschlagHinweis(meldung == null || meldung.isEmpty()
                    ? getString(R.string.nachschlagen_fehlgeschlagen) : meldung);
        });
    }

    /** Haengt den Grund hinten an die Zeile unter dem Kennungsfeld. */
    private void nachschlagHinweis(String grund) {
        if (binding == null) {
            return;
        }
        CharSequence bisher = binding.textKennungHinweis.getVisibility() == View.VISIBLE
                ? binding.textKennungHinweis.getText() : "";
        binding.textKennungHinweis.setVisibility(View.VISIBLE);
        binding.textKennungHinweis.setText(bisher.length() == 0
                ? grund : bisher + " · " + grund);
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
        binding.editMenge.setText(String.valueOf(artikel.menge));
        binding.schalterVerpackung.setChecked(artikel.istVerpackung);
        binding.editPackungsGroesse.setText(artikel.packungsGroesse > 0
                ? String.valueOf(artikel.packungsGroesse) : "");
        offenePackungen.clear();
        offenePackungen.addAll(Packungen.offene(artikel.angebrochen));
        mengenFelder();
        binding.schalterBehaelter.setChecked(artikel.istBehaelter);
        binding.dropdownBehaelterArt.setText(artikel.behaelterArt, false);
        liegtIn = artikel.behaelterKennung;
        verleihFelder();
        behaelterFelder();
        behaelterLaden();
        fotoAnzeigen();
    }

    // ----------------------------------------------------------------- Menge

    /** Zahl aus einem Feld; leer oder unsinnig zaehlt als der Ersatzwert. */
    private int zahl(com.google.android.material.textfield.TextInputEditText feld, int ersatz) {
        String roh = text(feld.getText()).trim();
        if (roh.isEmpty()) {
            return ersatz;
        }
        try {
            return Math.max(0, Integer.parseInt(roh));
        } catch (NumberFormatException unbrauchbar) {
            return ersatz;
        }
    }

    /** Aendert sich eine Zahl, stimmt die Zusammenfassung sonst nicht mehr. */
    private void zahlBeobachten(com.google.android.material.textfield.TextInputEditText feld) {
        feld.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int anzahl, int nach) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int vorher, int anzahl) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (binding != null) {
                    gesamtZeigen();
                }
            }
        });
    }

    /** Sichtbarkeit der Verpackungsfelder und die Liste der offenen Packungen. */
    private void mengenFelder() {
        boolean verpackung = binding.schalterVerpackung.isChecked();
        int sicht = verpackung ? View.VISIBLE : View.GONE;
        binding.inputPackungsGroesse.setVisibility(sicht);
        binding.textOffeneTitel.setVisibility(sicht);
        binding.listeOffene.setVisibility(sicht);
        binding.buttonAnbrechen.setVisibility(sicht);
        binding.inputMenge.setHint(getString(verpackung
                ? R.string.menge_packungen : R.string.menge_feld));
        offeneZeichnen();
        gesamtZeigen();
    }

    /** Je angebrochene Packung eine Zeile - mit eigenem Rest und Wegwerfen. */
    private void offeneZeichnen() {
        binding.listeOffene.removeAllViews();
        int jePackung = zahl(binding.editPackungsGroesse, 0);
        for (int i = 0; i < offenePackungen.size(); i++) {
            final int stelle = i;
            ZeileOffenePackungBinding zeile = ZeileOffenePackungBinding.inflate(
                    getLayoutInflater(), binding.listeOffene, false);
            zeile.textNummer.setText(getString(R.string.verpackung_nummer, i + 1));
            zeile.editRest.setText(String.valueOf(offenePackungen.get(stelle)));
            zeile.textVon.setText(jePackung > 0
                    ? getString(R.string.verpackung_von, jePackung) : "");
            zeile.editRest.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    if (binding == null || stelle >= offenePackungen.size()) {
                        return;
                    }
                    offenePackungen.set(stelle, zahl(zeile.editRest, 0));
                    gesamtZeigen();
                }
            });
            zeile.buttonWeg.setOnClickListener(v -> {
                if (stelle < offenePackungen.size()) {
                    offenePackungen.remove(stelle);
                    offeneZeichnen();
                    gesamtZeigen();
                }
            });
            binding.listeOffene.addView(zeile.getRoot());
        }
    }

    /** Eine weitere Packung anbrechen - eine volle wird dafuer aufgebraucht. */
    private void packungAnbrechen() {
        int voll = zahl(binding.editMenge, 0);
        int jePackung = zahl(binding.editPackungsGroesse, 0);
        if (voll <= 0 || jePackung <= 0
                || offenePackungen.size() >= Packungen.HOECHSTENS_OFFEN) {
            Toast.makeText(requireContext(), R.string.verpackung_nichts_anzubrechen,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        binding.editMenge.setText(String.valueOf(voll - 1));
        offenePackungen.add(jePackung);
        offeneZeichnen();
        gesamtZeigen();
    }

    /** Die Zusammenfassung unter den Feldern - sie rechnet mit. */
    private void gesamtZeigen() {
        if (!binding.schalterVerpackung.isChecked()) {
            binding.textGesamt.setVisibility(View.GONE);
            return;
        }
        int voll = zahl(binding.editMenge, 0);
        int jePackung = zahl(binding.editPackungsGroesse, 0);
        if (jePackung <= 0) {
            binding.textGesamt.setVisibility(View.GONE);
            return;
        }
        int gesamt = voll * jePackung;
        StringBuilder reste = new StringBuilder();
        for (int rest : offenePackungen) {
            gesamt += rest;
            if (reste.length() > 0) {
                reste.append(" + ");
            }
            reste.append(rest);
        }
        String beschreibung = reste.length() == 0
                ? getString(R.string.verpackung_gesamt_nur_voll, voll, jePackung)
                : getString(R.string.verpackung_gesamt_mit_offen, voll, jePackung,
                        reste.toString());
        binding.textGesamt.setVisibility(View.VISIBLE);
        binding.textGesamt.setText(android.text.Html.fromHtml(
                getString(R.string.verpackung_gesamt, gesamt, beschreibung),
                android.text.Html.FROM_HTML_MODE_LEGACY));
    }

    // ------------------------------------------------------------- Behaelter

    /** Vorschlaege fuer die Art - frei ueberschreibbar. */
    private void behaelterArtenLaden() {
        binding.dropdownBehaelterArt.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.behaelter_arten)));
    }

    /**
     * Sichtbarkeit und Hinweis. Ohne eigene Kennung kann nichts ein Behaelter
     * sein - sie klebt am Moebel und wird beim Einraeumen gescannt.
     */
    private void behaelterFelder() {
        boolean behaelterFall = binding.schalterBehaelter.isChecked();
        binding.inputBehaelterArt.setVisibility(behaelterFall ? View.VISIBLE : View.GONE);
        boolean ohneKennung = behaelterFall
                && text(binding.editKennung.getText()).trim().isEmpty();
        binding.textBehaelterHinweis.setVisibility(ohneKennung ? View.VISIBLE : View.GONE);
    }

    /**
     * Die Auswahl "liegt in" fuellen. Ausgelassen wird, was einen Ring ergaebe:
     * der Artikel selbst und alles, was schon in ihm steckt.
     */
    private void behaelterLaden() {
        repository.ladeAlleArtikel(alle -> {
            if (binding == null) {
                return;
            }
            behaelter.clear();
            behaelter.addAll(Behaelterkette.moegliche(alle, original));
            List<String> namen = new ArrayList<>();
            namen.add(getString(R.string.behaelter_nirgends));
            for (Artikel eintrag : behaelter) {
                namen.add(eintrag.behaelterArt == null || eintrag.behaelterArt.isEmpty()
                        ? eintrag.name : eintrag.behaelterArt + ": " + eintrag.name);
            }
            binding.dropdownLiegtIn.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, namen));
            binding.dropdownLiegtIn.setOnItemClickListener((eltern, sicht, stelle, id) ->
                    liegtIn = stelle == 0 ? null : behaelter.get(stelle - 1).rfidUid);
            liegtInAnzeigen(namen);
        });
    }

    private void liegtInAnzeigen(List<String> namen) {
        if (liegtIn == null || liegtIn.isEmpty()) {
            binding.dropdownLiegtIn.setText(namen.get(0), false);
            return;
        }
        for (int i = 0; i < behaelter.size(); i++) {
            if (liegtIn.equals(behaelter.get(i).rfidUid)) {
                binding.dropdownLiegtIn.setText(namen.get(i + 1), false);
                return;
            }
        }
        // Der Behaelter ist nicht (mehr) waehlbar - trotzdem zeigen, was dasteht.
        binding.dropdownLiegtIn.setText(liegtIn, false);
    }

    /** Ein gescannter Code soll den Behaelter treffen, nicht irgendetwas. */
    private void behaelterUebernehmen(String code) {
        repository.ladeAlleArtikel(alle -> {
            if (binding == null) {
                return;
            }
            Artikel treffer = null;
            for (Artikel eintrag : alle) {
                if (code.equals(eintrag.rfidUid)) {
                    treffer = eintrag;
                    break;
                }
            }
            if (treffer == null) {
                Toast.makeText(requireContext(),
                        getString(R.string.behaelter_unbekannt, code), Toast.LENGTH_LONG).show();
                return;
            }
            if (!treffer.istBehaelter) {
                Toast.makeText(requireContext(),
                        getString(R.string.behaelter_kein_behaelter, treffer.name),
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (original != null && treffer.id == original.id) {
                Toast.makeText(requireContext(), R.string.behaelter_selbst,
                        Toast.LENGTH_LONG).show();
                return;
            }
            boolean erlaubt = false;
            for (Artikel moeglich : Behaelterkette.moegliche(alle, original)) {
                if (moeglich.id == treffer.id) {
                    erlaubt = true;
                    break;
                }
            }
            if (!erlaubt) {
                Toast.makeText(requireContext(),
                        getString(R.string.behaelter_ring, treffer.name),
                        Toast.LENGTH_LONG).show();
                return;
            }
            liegtIn = treffer.rfidUid;
            behaelterLaden();
        });
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
        ziel.menge = zahl(binding.editMenge, 1);
        ziel.istVerpackung = binding.schalterVerpackung.isChecked();
        ziel.packungsGroesse = ziel.istVerpackung ? zahl(binding.editPackungsGroesse, 1) : 0;
        ziel.angebrochen = ziel.istVerpackung ? Packungen.alsText(offenePackungen) : null;
        // Raeumt Widersprueche weg, etwa einen Rest groesser als die Packung.
        Packungen.geradeziehen(ziel);
        ziel.istBehaelter = binding.schalterBehaelter.isChecked();
        ziel.behaelterArt = ziel.istBehaelter
                ? Formatter.leerZuNull(text(binding.dropdownBehaelterArt.getText())) : null;
        ziel.behaelterKennung = Formatter.leerZuNull(liegtIn);
        if (ziel.istBehaelter && ziel.rfidUid == null) {
            binding.editKennung.setError(getString(R.string.behaelter_braucht_kennung));
            binding.editKennung.requestFocus();
            behaelterFelder();
            return;
        }
        if (ziel.behaelterKennung != null && ziel.behaelterKennung.equals(ziel.rfidUid)) {
            Toast.makeText(requireContext(), R.string.behaelter_selbst, Toast.LENGTH_LONG).show();
            return;
        }
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
        binding.editMenge.setText("1");
        binding.schalterVerpackung.setChecked(false);
        binding.editPackungsGroesse.setText("");
        offenePackungen.clear();
        mengenFelder();
        binding.schalterBehaelter.setChecked(false);
        binding.dropdownBehaelterArt.setText("", false);
        liegtIn = null;
        verleihFelder();
        behaelterFelder();
        behaelterLaden();
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
                || binding.schalterBehaelter.isChecked() != original.istBehaelter
                || !wert(liegtIn).equals(wert(original.behaelterKennung))
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
