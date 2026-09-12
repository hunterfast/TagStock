package de.tagstock.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.tagstock.R;
import de.tagstock.data.Artikel;
import de.tagstock.data.ArtikelStatus;
import de.tagstock.data.Abgleich;
import de.tagstock.data.Packungen;
import de.tagstock.data.Protokoll;
import de.tagstock.data.Repository;
import de.tagstock.databinding.ActivityArtikelDetailBinding;
import de.tagstock.databinding.DialogMengeBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Einstellungen;
import de.tagstock.util.Formatter;
import de.tagstock.util.FotoLader;
import de.tagstock.util.Fotos;
import de.tagstock.util.Hintergrund;
import de.tagstock.util.NfcHelper;
import de.tagstock.util.PdfErzeuger;
import de.tagstock.util.QrErzeuger;
import de.tagstock.util.Randabstand;
import de.tagstock.util.ScanResult;
import de.tagstock.util.Serverbilder;
import de.tagstock.util.Sicherung;

/** Einzelansicht eines Artikels mit Protokoll, Historie und QR-Code. */
public class ArtikelDetailActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "de.tagstock.extra.ARTIKEL_ID";

    /** Die gewoehnliche Schriftfarbe der grossen Zahl - 0 heisst: noch unbekannt. */
    private int mengeFarbe;

    private ActivityArtikelDetailBinding binding;
    private Repository repository;
    private ProtokollAdapter protokollAdapter;

    private long artikelId;
    @Nullable
    private Artikel artikel;
    private boolean qrSichtbar;
    /** Wert, zu dem das angezeigte QR-Bild gehoert. */
    @Nullable
    private String qrWert;
    private boolean bearbeitet;
    @Nullable
    private androidx.appcompat.app.AlertDialog nfcDialog;

    private final ActivityResultLauncher<String> belegLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), this::belegSchreiben);

    private final ActivityResultLauncher<String> etikettLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), this::etikettSchreiben);

    /** Einraeumen: Der gescannte Gegenstand wandert in diesen Behaelter. */
    private final ActivityResultLauncher<Intent> einraeumScan = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), ergebnis -> {
                if (ergebnis.getResultCode() != android.app.Activity.RESULT_OK) {
                    return;
                }
                ScanResult scan = ScanResult.fromIntent(ergebnis.getData());
                if (scan != null) {
                    einraeumen(scan.code);
                }
            });

    public static Intent intent(Context context, long artikelId) {
        Intent intent = new Intent(context, ArtikelDetailActivity.class);
        intent.putExtra(EXTRA_ID, artikelId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Einstellungen.anwenden(this);
        super.onCreate(savedInstanceState);
        binding = ActivityArtikelDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Randabstand.anwenden(binding.getRoot());
        repository = Repository.getInstance(this);

        artikelId = getIntent().getLongExtra(EXTRA_ID, 0L);
        if (artikelId == 0L) {
            finish();
            return;
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> zurueck());

        protokollAdapter = new ProtokollAdapter();
        binding.recyclerProtokoll.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerProtokoll.setAdapter(protokollAdapter);
        binding.recyclerProtokoll.setNestedScrollingEnabled(false);

        binding.chipStatus.setOnClickListener(v -> statusWaehlen());
        binding.buttonStandortWechseln.setOnClickListener(v -> standortWaehlen());
        binding.headerQr.setOnClickListener(v -> {
            qrSichtbar = !qrSichtbar;
            qrZeichnen();
        });
        binding.buttonQrSpeichern.setOnClickListener(v ->
                etikettLauncher.launch(etikettName()));
        binding.buttonLeihbeleg.setOnClickListener(v ->
                belegLauncher.launch(belegName()));

        repository.beobachteArtikel(artikelId).observe(this, geladen -> {
            if (geladen == null) {
                finish();
                return;
            }
            artikel = geladen;
            zeichnen(geladen);
        });

        repository.beobachteProtokoll(artikelId).observe(this, eintraege -> {
            protokollAdapter.submitList(eintraege);
            binding.cardProtokoll.setVisibility(
                    eintraege == null || eintraege.isEmpty() ? View.GONE : View.VISIBLE);
            historieZeichnen(eintraege);
        });
    }

    // ------------------------------------------------------------------ Anzeige

    private void zeichnen(Artikel artikel) {
        setTitle(artikel.name);
        binding.textName.setText(artikel.name);

        FotoLader.laden(binding.imageFoto, artikel.fotoPfad, artikel.bildUrl, R.drawable.ic_artikel);

        Formatter.statusPlakette(binding.chipStatus, artikel.status);

        binding.textGescannt.setVisibility(artikel.zuletztGescannt == null
                ? View.GONE : View.VISIBLE);
        if (artikel.zuletztGescannt != null) {
            binding.textGescannt.setText(getString(R.string.detail_zuletzt_gescannt,
                    Formatter.datumZeit(this, artikel.zuletztGescannt)));
        }

        text(binding.textBeschreibung, artikel.beschreibung);
        wert(binding.gruppeKennung, binding.textKennung, artikel.rfidUid);
        wert(binding.gruppeKategorie, binding.textKategorie, artikel.kategorie);
        wert(binding.gruppeStandort, binding.textStandort, artikel.standort);
        wert(binding.gruppeLagerort, binding.textLagerort, artikel.lagerort);

        boolean verliehen = artikel.status == ArtikelStatus.VERLIEHEN;
        binding.cardVerliehen.setVisibility(verliehen ? View.VISIBLE : View.GONE);
        if (verliehen) {
            binding.textVerliehenAn.setText(artikel.verliehenAn == null
                    ? getString(R.string.detail_ohne_person) : artikel.verliehenAn);
            binding.textRueckgabe.setVisibility(artikel.rueckgabeDatum == null
                    ? View.GONE : View.VISIBLE);
            if (artikel.rueckgabeDatum != null) {
                binding.textRueckgabe.setText(artikel.istUeberfaellig()
                        ? getString(R.string.detail_ueberfaellig,
                        Formatter.tageUeberfaellig(artikel))
                        : getString(R.string.detail_rueckgabe,
                        Formatter.datum(this, artikel.rueckgabeDatum)));
            }
        }

        binding.textVermisst.setVisibility(artikel.istVermisst() ? View.VISIBLE : View.GONE);
        mengeZeichnen(artikel);
        behaelterZeichnen(artikel);
        qrZeichnen();
        invalidateOptionsMenu();
    }

    // ----------------------------------------------------------------- Menge

    /**
     * Gross steht, was man in die Hand nimmt - Packungen, wenn es welche
     * gibt, sonst Stueck. Klein darunter die Einzelteile insgesamt, denn die
     * Packungszahl allein sagt nicht, wie viele Shellys noch da sind.
     */
    private void mengeZeichnen(Artikel artikel) {
        int gesamt = Packungen.gesamt(artikel);
        boolean verpackung = artikel.istVerpackung && artikel.packungsGroesse > 0;

        binding.textMengeGross.setText(String.valueOf(verpackung ? artikel.menge : gesamt));
        binding.textMengeEinheit.setText(getString(verpackung
                ? R.string.menge_packungen : R.string.menge_stueck));
        // Die Farbe aus dem Thema einmal merken, sonst bleibt Rot kleben.
        if (mengeFarbe == 0) {
            mengeFarbe = binding.textMengeGross.getCurrentTextColor();
        }
        binding.textMengeGross.setTextColor(gesamt == 0
                ? androidx.core.content.ContextCompat.getColor(this, R.color.status_fehlt)
                : mengeFarbe);

        binding.textMengeGesamt.setVisibility(verpackung ? View.VISIBLE : View.GONE);
        if (verpackung) {
            binding.textMengeGesamt.setText(getString(R.string.menge_gesamt_stueck, gesamt)
                    + " · " + getString(R.string.menge_je_packung, artikel.packungsGroesse));
        }

        binding.gruppeOffene.removeAllViews();
        for (int rest : Packungen.offene(artikel.angebrochen)) {
            com.google.android.material.chip.Chip marke =
                    new com.google.android.material.chip.Chip(this);
            marke.setText(getString(R.string.verpackung_offen_kurz, rest));
            marke.setClickable(false);
            binding.gruppeOffene.addView(marke);
        }

        binding.buttonEntnehmen.setEnabled(gesamt > 0);
        binding.buttonEntnehmen.setOnClickListener(v -> mengeFragen(artikel, true));
        binding.buttonZugang.setOnClickListener(v -> mengeFragen(artikel, false));
        binding.buttonPackungAnbrechen.setVisibility(
                verpackung && artikel.menge > 0 ? View.VISIBLE : View.GONE);
        binding.buttonPackungAnbrechen.setOnClickListener(v ->
                repository.packungAnbrechen(artikel.id, Einstellungen.nutzer(this), erfolg -> {
                    if (!erfolg) {
                        Toast.makeText(this, R.string.verpackung_nichts_anzubrechen,
                                Toast.LENGTH_SHORT).show();
                    }
                    nachTragen();
                }));
    }

    /**
     * Wie viele? Vorgabe ist eins - das ist der haeufigste Fall -, mehr geht
     * ueber die Knoepfe oder durch Eintippen.
     */
    private void mengeFragen(Artikel artikel, boolean entnahme) {
        int vorhanden = Packungen.gesamt(artikel);
        boolean verpackung = artikel.istVerpackung && artikel.packungsGroesse > 0;
        DialogMengeBinding maske = DialogMengeBinding.inflate(getLayoutInflater());

        maske.textKopf.setText(entnahme
                ? getString(R.string.entnehmen_kopf, artikel.name, vorhanden)
                : getString(verpackung ? R.string.zugang_kopf_packungen
                        : R.string.zugang_kopf_stueck, artikel.name));
        maske.textHinweis.setVisibility(entnahme && verpackung ? View.VISIBLE : View.GONE);
        maske.editAnzahl.setText("1");

        Runnable danach = () -> {
            int anzahl = gelesen(maske.editAnzahl);
            int rest = entnahme
                    ? Math.max(0, vorhanden - anzahl)
                    : vorhanden + anzahl * (verpackung ? artikel.packungsGroesse : 1);
            maske.textDanach.setText(android.text.Html.fromHtml(
                    getString(R.string.entnehmen_danach, rest),
                    android.text.Html.FROM_HTML_MODE_LEGACY));
        };
        danach.run();
        maske.buttonWeniger.setOnClickListener(v -> {
            maske.editAnzahl.setText(String.valueOf(Math.max(1, gelesen(maske.editAnzahl) - 1)));
            danach.run();
        });
        maske.buttonMehr.setOnClickListener(v -> {
            maske.editAnzahl.setText(String.valueOf(gelesen(maske.editAnzahl) + 1));
            danach.run();
        });
        maske.editAnzahl.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                danach.run();
            }
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle(entnahme ? R.string.entnehmen_titel : R.string.zugang_titel)
                .setView(maske.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(entnahme ? R.string.entnehmen : R.string.zugang,
                        (dialog, welcher) -> {
                            int anzahl = gelesen(maske.editAnzahl);
                            String nutzer = Einstellungen.nutzer(this);
                            if (entnahme) {
                                repository.entnehmen(artikel.id, anzahl, nutzer, genommen -> {
                                    Toast.makeText(this, genommen == 0
                                            ? getString(R.string.entnehmen_nichts_da)
                                            : getString(R.string.entnehmen_erledigt, genommen,
                                                    Math.max(0, vorhanden - genommen)),
                                            Toast.LENGTH_SHORT).show();
                                    nachTragen();
                                });
                            } else {
                                repository.zugang(artikel.id, anzahl, nutzer, neu ->
                                        Toast.makeText(this,
                                                getString(R.string.zugang_erledigt, neu),
                                                Toast.LENGTH_SHORT).show());
                                nachTragen();
                            }
                        })
                .show();
    }

    private int gelesen(com.google.android.material.textfield.TextInputEditText eingabe) {
        String roh = eingabe.getText() == null ? "" : eingabe.getText().toString().trim();
        if (roh.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(roh));
        } catch (NumberFormatException unbrauchbar) {
            return 1;
        }
    }

    /** Nach einer Mengenaenderung weiterreichen, damit der Server es erfaehrt. */
    private void nachTragen() {
        if (Einstellungen.serverAktiv(this)) {
            Abgleich.ausfuehren(getApplicationContext(), abgleich -> {
            });
        }
    }

    // ------------------------------------------------------------- Behaelter

    /**
     * Zwei Seiten derselben Sache: Wo liegt dieser Artikel - und, wenn er
     * selbst ein Behaelter ist, was liegt in ihm.
     */
    private void behaelterZeichnen(Artikel artikel) {
        binding.gruppeLiegtIn.setVisibility(View.GONE);
        if (artikel.behaelterKennung != null && !artikel.behaelterKennung.isEmpty()) {
            repository.ladeNachKennung(artikel.behaelterKennung, behaelter -> {
                binding.gruppeLiegtIn.setVisibility(View.VISIBLE);
                binding.buttonLiegtIn.setText(behaelter == null
                        ? artikel.behaelterKennung : behaelter.name);
                binding.buttonLiegtIn.setOnClickListener(behaelter == null ? null : v ->
                        startActivity(ArtikelDetailActivity.intent(this, behaelter.id)));
                binding.buttonLiegtIn.setEnabled(behaelter != null);
            });
        }

        boolean istBehaelter = artikel.istBehaelter
                && artikel.rfidUid != null && !artikel.rfidUid.isEmpty();
        binding.cardInhalt.setVisibility(istBehaelter ? View.VISIBLE : View.GONE);
        if (!istBehaelter) {
            return;
        }
        binding.buttonEinraeumen.setOnClickListener(v ->
                einraeumScan.launch(ScannerActivity.intent(this)));
        repository.ladeInhalt(artikel.rfidUid, inhalt -> {
            binding.textInhaltTitel.setText(getString(R.string.behaelter_inhalt, inhalt.size()));
            binding.textInhaltLeer.setVisibility(inhalt.isEmpty() ? View.VISIBLE : View.GONE);
            binding.gruppeInhalt.removeAllViews();
            for (Artikel drin : inhalt) {
                com.google.android.material.chip.Chip marke =
                        new com.google.android.material.chip.Chip(this);
                marke.setText(drin.istBehaelter ? drin.name + " ▸" : drin.name);
                marke.setOnClickListener(v ->
                        startActivity(ArtikelDetailActivity.intent(this, drin.id)));
                binding.gruppeInhalt.addView(marke);
            }
        });
    }

    /** Gescannten Gegenstand in diesen Behaelter legen. */
    private void einraeumen(String code) {
        if (artikel == null) {
            return;
        }
        Artikel behaelter = artikel;
        repository.ladeNachKennung(code, gefunden -> {
            if (gefunden == null) {
                Toast.makeText(this, getString(R.string.behaelter_unbekannt, code),
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (gefunden.id == behaelter.id) {
                Toast.makeText(this, R.string.behaelter_selbst, Toast.LENGTH_LONG).show();
                return;
            }
            repository.einraeumen(gefunden, behaelter, Einstellungen.nutzer(this), erfolg -> {
                if (!erfolg) {
                    Toast.makeText(this, getString(R.string.behaelter_ring, gefunden.name),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this, getString(R.string.behaelter_eingeraeumt,
                        gefunden.name, behaelter.name), Toast.LENGTH_SHORT).show();
                behaelterZeichnen(behaelter);
                if (Einstellungen.serverAktiv(this)) {
                    Abgleich.ausfuehren(getApplicationContext(), abgleich -> {
                    });
                }
            });
        });
    }

    private void text(android.widget.TextView view, @Nullable String wert) {
        view.setVisibility(wert == null || wert.trim().isEmpty() ? View.GONE : View.VISIBLE);
        view.setText(wert);
    }

    private void wert(View gruppe, android.widget.TextView view, @Nullable String wert) {
        boolean leer = wert == null || wert.trim().isEmpty();
        gruppe.setVisibility(leer ? View.GONE : View.VISIBLE);
        view.setText(wert);
    }

    private void qrZeichnen() {
        binding.gruppeQr.setVisibility(qrSichtbar ? View.VISIBLE : View.GONE);
        binding.textQrAktion.setText(qrSichtbar ? R.string.detail_ausblenden : R.string.detail_anzeigen);
        if (!qrSichtbar || artikel == null) {
            return;
        }
        String wert = artikel.qrWert();
        binding.textQrWert.setText(wert);
        if (wert.equals(qrWert)) {
            // Der Code haengt schon in der Ansicht; jede Neuzeichnung spart Rechnerei.
            return;
        }
        Hintergrund.starte(() -> QrErzeuger.erzeuge(wert, 480), qr -> {
            if (qr == null || !wert.equals(artikel == null ? null : artikel.qrWert())) {
                return;
            }
            qrWert = wert;
            binding.imageQr.setImageBitmap(qr);
        }, fehler -> {
            // Ohne QR-Bild bleibt der Wert als Text stehen.
        });
    }

    /** Aus dem Protokoll die Standortwechsel als Verlauf darstellen. */
    private void historieZeichnen(@Nullable List<Protokoll> eintraege) {
        List<Protokoll> wechsel = new ArrayList<>();
        if (eintraege != null) {
            for (Protokoll eintrag : eintraege) {
                if (eintrag.istStandortWechsel()) {
                    wechsel.add(eintrag);
                }
            }
        }
        binding.cardHistorie.setVisibility(wechsel.isEmpty() ? View.GONE : View.VISIBLE);
        binding.containerHistorie.removeAllViews();
        if (wechsel.isEmpty() || artikel == null) {
            return;
        }

        binding.containerHistorie.addView(historieZeile(
                getString(R.string.detail_historie_aktuell),
                artikel.standort == null ? "—" : artikel.standort, null));
        for (Protokoll eintrag : wechsel) {
            String wert = eintrag.neuerWert == null ? "—" : eintrag.neuerWert;
            String zusatz = eintrag.alterWert == null || eintrag.alterWert.isEmpty()
                    ? null : getString(R.string.detail_historie_vorher, eintrag.alterWert);
            binding.containerHistorie.addView(historieZeile(
                    Formatter.datum(this, eintrag.zeitpunkt), wert, zusatz));
        }
    }

    private View historieZeile(String titel, String wert, @Nullable String zusatz) {
        View view = getLayoutInflater().inflate(R.layout.item_historie,
                binding.containerHistorie, false);
        ((android.widget.TextView) view.findViewById(R.id.textZeitpunkt)).setText(titel);
        ((android.widget.TextView) view.findViewById(R.id.textWert)).setText(wert);
        android.widget.TextView zusatzText = view.findViewById(R.id.textZusatz);
        zusatzText.setVisibility(zusatz == null ? View.GONE : View.VISIBLE);
        zusatzText.setText(zusatz);
        return view;
    }

    // ------------------------------------------------------------------ Aktionen

    private void statusWaehlen() {
        if (artikel == null || !Einstellungen.darfBearbeiten(this)) {
            return;
        }
        ArtikelStatus[] werte = ArtikelStatus.values();
        String[] namen = new String[werte.length];
        for (int i = 0; i < werte.length; i++) {
            namen[i] = getString(werte[i].labelRes);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detail_status_aendern)
                .setItems(namen, (dialog, welcher) -> {
                    ArtikelStatus neu = werte[welcher];
                    if (neu == ArtikelStatus.VERLIEHEN) {
                        Dialogs.textInput(this, getString(R.string.artikel_verliehen_an),
                                getString(R.string.artikel_verliehen_an), artikel.verliehenAn,
                                person -> verleihen(person));
                        return;
                    }
                    repository.statusSetzen(artikel, neu, Einstellungen.nutzer(this), erfolg ->
                            Toast.makeText(this, getString(R.string.detail_status_gesetzt,
                                    getString(neu.labelRes)), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void verleihen(String person) {
        if (artikel == null) {
            return;
        }
        Artikel geaendert = artikel.kopie();
        geaendert.status = ArtikelStatus.VERLIEHEN;
        geaendert.verliehenAn = person;
        repository.speichern(geaendert, artikel, Einstellungen.nutzer(this), ergebnis ->
                Toast.makeText(this, getString(R.string.detail_verliehen_an, person),
                        Toast.LENGTH_SHORT).show());
    }

    private void standortWaehlen() {
        if (artikel == null || !Einstellungen.darfBearbeiten(this)) {
            return;
        }
        repository.ladeAlleArtikel(alle -> {
            List<String> standorte = new ArrayList<>();
            for (Artikel eintrag : alle) {
                if (eintrag.standort != null && !eintrag.standort.trim().isEmpty()
                        && !standorte.contains(eintrag.standort)) {
                    standorte.add(eintrag.standort);
                }
            }
            Collections.sort(standorte, String.CASE_INSENSITIVE_ORDER);
            List<String> eintraege = new ArrayList<>(standorte);
            eintraege.add(getString(R.string.bestand_standort_neu));

            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.detail_standort_wechseln)
                    .setItems(eintraege.toArray(new String[0]), (dialog, welcher) -> {
                        if (welcher == standorte.size()) {
                            Dialogs.textInput(this, getString(R.string.bestand_standort_neu),
                                    getString(R.string.artikel_standort), null,
                                    this::standortSetzen);
                        } else {
                            standortSetzen(standorte.get(welcher));
                        }
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        });
    }

    private void standortSetzen(String standort) {
        if (artikel == null) {
            return;
        }
        repository.standortSetzen(artikel, standort, Einstellungen.nutzer(this), erfolg ->
                Toast.makeText(this, getString(R.string.detail_standort_gesetzt, standort),
                        Toast.LENGTH_SHORT).show());
    }

    // ------------------------------------------------------------------- Menue

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_artikel_detail, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean darf = Einstellungen.darfBearbeiten(this);
        menu.findItem(R.id.action_bearbeiten).setVisible(darf && !bearbeitet);
        menu.findItem(R.id.action_loeschen).setVisible(darf && !bearbeitet);
        menu.findItem(R.id.action_leihbeleg).setVisible(
                artikel != null && artikel.status == ArtikelStatus.VERLIEHEN);
        menu.findItem(R.id.action_etikett).setVisible(!bearbeitet);
        menu.findItem(R.id.action_nfc).setVisible(
                darf && !bearbeitet && NfcHelper.hasHardware(this));
        menu.findItem(R.id.action_anfragen).setVisible(!bearbeitet && artikel != null
                && artikel.serverId != null && Einstellungen.serverAktiv(this));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            zurueck();
            return true;
        }
        if (id == R.id.action_bearbeiten) {
            bearbeitenStarten();
            return true;
        }
        if (id == R.id.action_loeschen && artikel != null) {
            Dialogs.confirm(this, getString(R.string.artikel_loeschen_titel),
                    getString(R.string.artikel_loeschen_text, artikel.name),
                    R.string.action_delete, () -> {
                        Fotos.loeschen(this, artikel.fotoPfad);
                        Serverbilder.entfernen(this, artikel.bildUrl);
                        repository.loeschen(artikel);
                        Toast.makeText(this, R.string.artikel_geloescht, Toast.LENGTH_SHORT).show();
                        finish();
                    });
            return true;
        }
        if (id == R.id.action_leihbeleg) {
            belegLauncher.launch(belegName());
            return true;
        }
        if (id == R.id.action_etikett) {
            etikettLauncher.launch(etikettName());
            return true;
        }
        if (id == R.id.action_nfc) {
            nfcSchreiben();
            return true;
        }
        if (id == R.id.action_anfragen) {
            anfrageStellen();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void bearbeitenStarten() {
        bearbeitet = true;
        binding.scrollDetail.setVisibility(View.GONE);
        binding.formContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.formContainer, ArtikelFormFragment.bearbeiten(artikelId), "form")
                .commit();
        invalidateOptionsMenu();
    }

    /** Wird vom Formular nach dem Speichern aufgerufen. */
    public void bearbeitenBeenden() {
        bearbeitet = false;
        binding.formContainer.setVisibility(View.GONE);
        binding.scrollDetail.setVisibility(View.VISIBLE);
        androidx.fragment.app.Fragment form = getSupportFragmentManager().findFragmentByTag("form");
        if (form != null) {
            getSupportFragmentManager().beginTransaction().remove(form).commit();
        }
        invalidateOptionsMenu();
    }

    private void zurueck() {
        if (bearbeitet) {
            bearbeitenBeenden();
            return;
        }
        finish();
    }

    // --------------------------------------------------------------- Anfrage

    /** Fragt beim Team an, ob man den Artikel ausleihen darf. */
    private void anfrageStellen() {
        String url = Einstellungen.serverUrl(this);
        String token = Einstellungen.token(this);
        String teamId = Einstellungen.teamId(this);
        if (artikel == null || url == null || token == null || teamId == null) {
            return;
        }
        String serverId = artikel.serverId;
        Dialogs.textInput(this, getString(R.string.anfrage_stellen),
                getString(R.string.anfrage_nachricht), null,
                nachricht -> de.tagstock.util.Hintergrund.starte(
                        () -> new de.tagstock.util.ServerClient(url, token)
                                .anfrageStellen(teamId, serverId, nachricht, null),
                        ergebnis -> Toast.makeText(this, R.string.anfrage_gestellt,
                                Toast.LENGTH_SHORT).show(),
                        fehler -> Toast.makeText(this, String.valueOf(fehler.getMessage()),
                                Toast.LENGTH_LONG).show()));
    }

    // -------------------------------------------------------------------- NFC

    /** Schreibt Name und Nummer auf ein Tag und uebernimmt dessen Kennung. */
    private void nfcSchreiben() {
        if (artikel == null) {
            return;
        }
        if (!NfcHelper.isReady(this)) {
            Toast.makeText(this, R.string.nfc_nicht_bereit, Toast.LENGTH_LONG).show();
            return;
        }
        nfcDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nfc_schreiben_titel)
                .setMessage(R.string.nfc_schreiben_text)
                .setNegativeButton(R.string.action_cancel, (dialog, welcher) -> nfcBeenden())
                .setOnDismissListener(dialog -> NfcHelper.disableReader(this))
                .show();

        String inhalt = "TagStock #" + artikel.id + " " + artikel.name;
        Artikel dieser = artikel;
        NfcHelper.enableReader(this, tag -> {
            // Laeuft schon im Hintergrund - hier darf nachgesehen und geschrieben werden.
            String kennung = NfcHelper.toHex(tag.getId());
            Artikel belegt = repository.kennungDirekt(kennung);
            if (belegt != null && belegt.id != dieser.id) {
                // Ein fremder Tag wird nicht ueberschrieben.
                runOnUiThread(() -> {
                    nfcBeenden();
                    tagGehoertAnderem(belegt);
                });
                return;
            }
            NfcHelper.Schreibergebnis ergebnis = NfcHelper.schreibe(tag, inhalt);
            runOnUiThread(() -> {
                Toast.makeText(this, ergebnis.meldungRes, Toast.LENGTH_LONG).show();
                nfcBeenden();
                if (ergebnis != NfcHelper.Schreibergebnis.OK || artikel == null) {
                    return;
                }
                if (artikel.rfidUid == null || artikel.rfidUid.isEmpty()) {
                    kennungUebernehmen(kennung);
                } else if (!artikel.rfidUid.equals(kennung)) {
                    kennungErsetzenFragen(kennung);
                }
            });
        });
    }

    /** Der Tag steckt schon an einem anderen Artikel - zeigen, an welchem. */
    private void tagGehoertAnderem(Artikel belegt) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.kennung_belegt_titel)
                .setMessage(getString(R.string.nfc_tag_belegt, belegt.name))
                .setNegativeButton(R.string.action_ok, null)
                .setPositiveButton(R.string.treffer_oeffnen, (dialog, welcher) ->
                        startActivity(intent(this, belegt.id)))
                .show();
    }

    private void kennungErsetzenFragen(String kennung) {
        if (artikel == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.nfc_kennung_ersetzen_titel)
                .setMessage(getString(R.string.nfc_kennung_ersetzen_text, artikel.name,
                        artikel.rfidUid))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.nfc_kennung_ersetzen,
                        (dialog, welcher) -> kennungUebernehmen(kennung))
                .show();
    }

    private void kennungUebernehmen(String kennung) {
        if (artikel == null) {
            return;
        }
        Artikel mitKennung = artikel.kopie();
        mitKennung.rfidUid = kennung;
        repository.speichern(mitKennung, artikel, Einstellungen.nutzer(this), gespeichert -> {
            if (gespeichert.erfolgreich) {
                return;
            }
            if (gespeichert.kennungBelegtVon != null) {
                tagGehoertAnderem(gespeichert.kennungBelegtVon);
            } else {
                Toast.makeText(this, R.string.kennung_belegt_unbekannt,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void nfcBeenden() {
        NfcHelper.disableReader(this);
        if (nfcDialog != null && nfcDialog.isShowing()) {
            nfcDialog.dismiss();
        }
        nfcDialog = null;
    }

    @Override
    protected void onPause() {
        if (nfcDialog != null) {
            nfcBeenden();
        }
        super.onPause();
    }

    // -------------------------------------------------------------------- PDF

    private String belegName() {
        return Sicherung.dateiname("pdf").replace(".pdf", "-leihbeleg.pdf");
    }

    private String etikettName() {
        return Sicherung.dateiname("pdf").replace(".pdf", "-etikett.pdf");
    }

    private void belegSchreiben(@Nullable Uri ziel) {
        if (ziel == null || artikel == null) {
            return;
        }
        Artikel kopie = artikel.kopie();
        Hintergrund.starte(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(ziel, "wt")) {
                PdfErzeuger.leihbeleg(this, kopie, out);
            }
            return true;
        }, fertig -> Toast.makeText(this, R.string.leihbeleg_fertig, Toast.LENGTH_LONG).show(),
                fehler -> Toast.makeText(this, getString(R.string.etiketten_fehler,
                        String.valueOf(fehler.getMessage())), Toast.LENGTH_LONG).show());
    }

    private void etikettSchreiben(@Nullable Uri ziel) {
        if (ziel == null || artikel == null) {
            return;
        }
        List<Artikel> eines = Collections.singletonList(artikel.kopie());
        Hintergrund.starte(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(ziel, "wt")) {
                PdfErzeuger.etiketten(this, eines, out);
            }
            return true;
        }, fertig -> Toast.makeText(this, getString(R.string.etiketten_fertig, 1),
                Toast.LENGTH_LONG).show(),
                fehler -> Toast.makeText(this, getString(R.string.etiketten_fehler,
                        String.valueOf(fehler.getMessage())), Toast.LENGTH_LONG).show());
    }
}
