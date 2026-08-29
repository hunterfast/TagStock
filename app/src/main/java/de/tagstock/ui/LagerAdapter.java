package de.tagstock.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.tagstock.R;
import de.tagstock.data.Lager;
import de.tagstock.data.LagerWithCount;
import de.tagstock.databinding.ItemLagerBinding;

/** Zeigt die Lager mit den Artikelzahlen je Status. */
public class LagerAdapter extends ListAdapter<LagerWithCount, LagerAdapter.LagerViewHolder> {

    public interface Listener {
        void onLagerClick(Lager lager);

        void onLagerLongClick(Lager lager);
    }

    private final Listener listener;

    public LagerAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<LagerWithCount> DIFF =
            new DiffUtil.ItemCallback<LagerWithCount>() {
                @Override
                public boolean areItemsTheSame(@NonNull LagerWithCount a, @NonNull LagerWithCount b) {
                    return a.lager.id == b.lager.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull LagerWithCount a, @NonNull LagerWithCount b) {
                    return a.lager.name.equals(b.lager.name)
                            && equal(a.lager.ort, b.lager.ort)
                            && equal(a.lager.beschreibung, b.lager.beschreibung)
                            && a.gesamt == b.gesamt
                            && a.vorhanden == b.vorhanden
                            && a.verliehen == b.verliehen
                            && a.verloren == b.verloren;
                }

                private boolean equal(String a, String b) {
                    return a == null ? b == null : a.equals(b);
                }
            };

    @NonNull
    @Override
    public LagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLagerBinding binding = ItemLagerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LagerViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LagerViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class LagerViewHolder extends RecyclerView.ViewHolder {

        private final ItemLagerBinding binding;

        LagerViewHolder(ItemLagerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(LagerWithCount entry, Listener listener) {
            Context context = binding.getRoot().getContext();
            binding.textName.setText(entry.lager.name);

            String ort = entry.lager.ort;
            if (ort == null || ort.trim().isEmpty()) {
                ort = entry.lager.beschreibung;
            }
            if (ort == null || ort.trim().isEmpty()) {
                binding.textOrt.setVisibility(View.GONE);
            } else {
                binding.textOrt.setVisibility(View.VISIBLE);
                binding.textOrt.setText(ort);
            }

            binding.textGesamt.setText(context.getString(R.string.lager_artikel_anzahl, entry.gesamt));
            bindCount(binding.textVorhanden, entry.vorhanden, R.string.status_vorhanden,
                    R.color.status_vorhanden);
            bindCount(binding.textVerliehen, entry.verliehen, R.string.status_verliehen,
                    R.color.status_verliehen);
            bindCount(binding.textVerloren, entry.verloren, R.string.status_verloren,
                    R.color.status_verloren);

            binding.getRoot().setOnClickListener(v -> listener.onLagerClick(entry.lager));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onLagerLongClick(entry.lager);
                return true;
            });
        }

        private void bindCount(android.widget.TextView view, int count, int labelRes, int colorRes) {
            Context context = view.getContext();
            view.setText(count + " " + context.getString(labelRes).toLowerCase(
                    java.util.Locale.getDefault()));
            view.setTextColor(ContextCompat.getColor(context, colorRes));
            view.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        }
    }
}
