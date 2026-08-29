package de.tagstock.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.tagstock.R;
import de.tagstock.data.Lager;
import de.tagstock.data.Repository;
import de.tagstock.databinding.DialogLagerEditBinding;

/** Dialog zum Anlegen und Bearbeiten eines Lagers. */
public final class LagerDialog {

    private LagerDialog() {
    }

    /**
     * Zeigt den Dialog an.
     *
     * @param lager bestehendes Lager oder null zum Anlegen
     */
    public static void show(Context context, Repository repository, @Nullable Lager lager,
                            @Nullable Runnable onSaved) {
        DialogLagerEditBinding binding = DialogLagerEditBinding.inflate(LayoutInflater.from(context));
        if (lager != null) {
            binding.editLagerName.setText(lager.name);
            binding.editLagerOrt.setText(lager.ort);
            binding.editLagerBeschreibung.setText(lager.beschreibung);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(lager == null ? R.string.lager_neu : R.string.lager_bearbeiten)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = value(binding.editLagerName.getText());
                    if (name.isEmpty()) {
                        Toast.makeText(context, R.string.lager_name_fehlt, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String ort = nullIfEmpty(value(binding.editLagerOrt.getText()));
                    String beschreibung = nullIfEmpty(value(binding.editLagerBeschreibung.getText()));

                    if (lager == null) {
                        repository.insertLager(new Lager(name, beschreibung, ort), id -> {
                            Toast.makeText(context, R.string.lager_gespeichert, Toast.LENGTH_SHORT).show();
                            if (onSaved != null) {
                                onSaved.run();
                            }
                        });
                    } else {
                        lager.name = name;
                        lager.ort = ort;
                        lager.beschreibung = beschreibung;
                        repository.updateLager(lager);
                        Toast.makeText(context, R.string.lager_gespeichert, Toast.LENGTH_SHORT).show();
                        if (onSaved != null) {
                            onSaved.run();
                        }
                    }
                })
                .show();
    }

    private static String value(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    @Nullable
    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
