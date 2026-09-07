package de.tagstock.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.tagstock.R;
import de.tagstock.data.Protokoll;
import de.tagstock.databinding.ItemProtokollBinding;
import de.tagstock.util.Formatter;

/** Eintraege des Aenderungsprotokolls. */
public class ProtokollAdapter extends ListAdapter<Protokoll, ProtokollAdapter.ProtokollHolder> {

    public ProtokollAdapter() {
        super(DIFF);
    }

    private static final DiffUtil.ItemCallback<Protokoll> DIFF =
            new DiffUtil.ItemCallback<Protokoll>() {
                @Override
                public boolean areItemsTheSame(@NonNull Protokoll a, @NonNull Protokoll b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Protokoll a, @NonNull Protokoll b) {
                    return a.zeitpunkt == b.zeitpunkt && a.aktion.equals(b.aktion);
                }
            };

    @NonNull
    @Override
    public ProtokollHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ProtokollHolder(ItemProtokollBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ProtokollHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ProtokollHolder extends RecyclerView.ViewHolder {

        private final ItemProtokollBinding binding;

        ProtokollHolder(ItemProtokollBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Protokoll eintrag) {
            android.content.Context context = binding.getRoot().getContext();
            binding.textAktion.setText(eintrag.aktion);

            boolean hatWechsel = eintrag.alterWert != null && !eintrag.alterWert.isEmpty()
                    && eintrag.neuerWert != null && !eintrag.neuerWert.isEmpty();
            binding.textWechsel.setVisibility(hatWechsel ? View.VISIBLE : View.GONE);
            if (hatWechsel) {
                binding.textWechsel.setText(context.getString(R.string.protokoll_wechsel,
                        eintrag.alterWert, eintrag.neuerWert));
            }

            String nutzer = eintrag.nutzer == null || eintrag.nutzer.isEmpty()
                    ? context.getString(R.string.protokoll_ohne_nutzer) : eintrag.nutzer;
            binding.textFuss.setText(context.getString(R.string.protokoll_fuss, nutzer,
                    Formatter.datumZeit(context, eintrag.zeitpunkt)));
        }
    }
}
