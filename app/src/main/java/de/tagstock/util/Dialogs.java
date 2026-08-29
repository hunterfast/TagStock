package de.tagstock.util;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.tagstock.R;
import de.tagstock.databinding.DialogTextInputBinding;

/** Wiederverwendete Dialoge. */
public final class Dialogs {

    public interface OnText {
        void onText(String value);
    }

    private Dialogs() {
    }

    /** Einzeiliger Texteingabe-Dialog. Leere Eingaben werden verworfen. */
    public static void textInput(Context context, String title, String hint,
                                 @Nullable String preset, OnText callback) {
        DialogTextInputBinding binding = DialogTextInputBinding.inflate(LayoutInflater.from(context));
        binding.inputText.setHint(hint);
        if (preset != null) {
            binding.editText.setText(preset);
            binding.editText.setSelection(preset.length());
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    String value = binding.editText.getText() == null
                            ? "" : binding.editText.getText().toString().trim();
                    if (!value.isEmpty()) {
                        callback.onText(value);
                    }
                })
                .show();
    }

    public static void confirm(Context context, String title, String message,
                               int positiveLabelRes, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(positiveLabelRes, (dialog, which) -> onConfirm.run())
                .show();
    }
}
