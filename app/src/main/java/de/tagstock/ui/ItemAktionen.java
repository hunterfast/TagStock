package de.tagstock.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import de.tagstock.R;
import de.tagstock.data.ItemWithState;
import de.tagstock.data.Repository;
import de.tagstock.data.Verleih;
import de.tagstock.databinding.DialogAusleihenBinding;
import de.tagstock.util.Dialogs;
import de.tagstock.util.Formatter;

/** Ausleihen, Zuruecknehmen und Verlustmeldungen - von Liste und Detail genutzt. */
public final class ItemAktionen {

    private ItemAktionen() {
    }

    /** Verleiht Stuecke an eine Person. */
    public static void ausleihen(Context context, Repository repository, ItemWithState state,
                                 @Nullable Runnable danach) {
        int vorhanden = state.vorhanden();
        if (vorhanden <= 0) {
            Toast.makeText(context, R.string.verleih_nichts_vorhanden, Toast.LENGTH_SHORT).show();
            return;
        }

        DialogAusleihenBinding binding = DialogAusleihenBinding.inflate(LayoutInflater.from(context));
        binding.editMenge.setText(String.valueOf(1));
        binding.inputMenge.setHelperText(context.getString(R.string.verleih_max, vorhanden));
        binding.inputMenge.setVisibility(vorhanden > 1 ? android.view.View.VISIBLE
                : android.view.View.GONE);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.verleih_titel)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.verleih_bestaetigen, (dialog, welcher) -> {
                    String person = text(binding.editPerson.getText());
                    if (person.isEmpty()) {
                        Toast.makeText(context, R.string.verleih_person_fehlt,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int menge = zahl(text(binding.editMenge.getText()), 1);
                    menge = Math.max(1, Math.min(menge, vorhanden));
                    String notiz = text(binding.editNotiz.getText());

                    repository.ausleihen(state.item.id, person, menge,
                            notiz.isEmpty() ? null : notiz, erfolg -> {
                                Toast.makeText(context, erfolg
                                                ? context.getString(R.string.verleih_gebucht, person)
                                                : context.getString(R.string.verleih_fehlgeschlagen),
                                        Toast.LENGTH_SHORT).show();
                                if (danach != null) {
                                    danach.run();
                                }
                            });
                })
                .show();
    }

    /** Nimmt einen offenen Vorgang zurueck; bei mehreren wird gefragt, welcher. */
    public static void zurueckgeben(Context context, Repository repository, long itemId,
                                    @Nullable Runnable danach) {
        repository.ladeOffeneVerleihe(itemId, offene -> {
            if (offene.isEmpty()) {
                Toast.makeText(context, R.string.verleih_nichts_offen, Toast.LENGTH_SHORT).show();
                return;
            }
            if (offene.size() == 1) {
                zurueckBuchen(context, repository, offene.get(0), danach);
                return;
            }
            String[] eintraege = new String[offene.size()];
            for (int i = 0; i < offene.size(); i++) {
                eintraege[i] = Formatter.verleihZeile(context, offene.get(i));
            }
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.verleih_zurueck_titel)
                    .setItems(eintraege, (dialog, welcher) ->
                            zurueckBuchen(context, repository, offene.get(welcher), danach))
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        });
    }

    public static void zurueckBuchen(Context context, Repository repository, Verleih verleih,
                                     @Nullable Runnable danach) {
        repository.zurueckgeben(verleih, erfolg -> {
            Toast.makeText(context, context.getString(R.string.verleih_zurueck_gebucht,
                    verleih.person), Toast.LENGTH_SHORT).show();
            if (danach != null) {
                danach.run();
            }
        });
    }

    /** Meldet Stuecke als verloren. */
    public static void verlorenMelden(Context context, Repository repository, ItemWithState state,
                                      @Nullable Runnable danach) {
        int vorhanden = state.vorhanden();
        if (vorhanden <= 0) {
            Toast.makeText(context, R.string.verlust_nichts_vorhanden, Toast.LENGTH_SHORT).show();
            return;
        }
        if (vorhanden == 1) {
            melden(context, repository, state.item.id, 1, danach);
            return;
        }
        Dialogs.zahlInput(context, context.getString(R.string.verlust_titel),
                context.getString(R.string.verlust_menge), vorhanden, vorhanden,
                menge -> melden(context, repository, state.item.id, menge, danach));
    }

    private static void melden(Context context, Repository repository, long itemId, int menge,
                               @Nullable Runnable danach) {
        repository.verlorenMelden(itemId, menge, erfolg -> {
            Toast.makeText(context, erfolg ? context.getString(R.string.verlust_gemeldet)
                    : context.getString(R.string.verlust_fehlgeschlagen), Toast.LENGTH_SHORT).show();
            if (danach != null) {
                danach.run();
            }
        });
    }

    /** Nimmt eine Verlustmeldung zurueck. */
    public static void wiedergefunden(Context context, Repository repository, ItemWithState state,
                                      @Nullable Runnable danach) {
        int verloren = state.verloren();
        if (verloren <= 0) {
            Toast.makeText(context, R.string.verlust_nichts_gemeldet, Toast.LENGTH_SHORT).show();
            return;
        }
        if (verloren == 1) {
            zuruecknehmen(context, repository, state.item.id, 1, danach);
            return;
        }
        Dialogs.zahlInput(context, context.getString(R.string.verlust_gefunden_titel),
                context.getString(R.string.verlust_menge), verloren, verloren,
                menge -> zuruecknehmen(context, repository, state.item.id, menge, danach));
    }

    private static void zuruecknehmen(Context context, Repository repository, long itemId,
                                      int menge, @Nullable Runnable danach) {
        repository.verlustZuruecknehmen(itemId, menge, erfolg -> {
            Toast.makeText(context, R.string.verlust_zurueckgenommen, Toast.LENGTH_SHORT).show();
            if (danach != null) {
                danach.run();
            }
        });
    }

    private static String text(CharSequence wert) {
        return wert == null ? "" : wert.toString().trim();
    }

    private static int zahl(String wert, int ersatz) {
        try {
            return Integer.parseInt(wert);
        } catch (NumberFormatException e) {
            return ersatz;
        }
    }

    /** Hilfsmethode fuer Listen: sind offene Ausleihen vorhanden? */
    public static boolean hatOffene(List<Verleih> verleihe) {
        for (Verleih verleih : verleihe) {
            if (verleih.istOffen()) {
                return true;
            }
        }
        return false;
    }
}
