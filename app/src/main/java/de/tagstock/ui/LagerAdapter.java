package de.tagstock.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.tagstock.R;
import de.tagstock.data.Lager;
import de.tagstock.data.LagerWithCount;
import de.tagstock.databinding.ItemLagerBinding;

/** Zeigt die Lager mit den Stueckzahlen je Zustand. */
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
                            && gleich(a.lager.ort, b.lager.ort)
                            && gleich(a.lager.beschreibung, b.lager.beschreibung)
                            && a.gesamt == b.gesamt
                            && a.artikel == b.artikel
                            && a.verliehen == b.verliehen
                            && a.verloren == b.verloren;
                }

                private boolean gleich(String a, String b) {
                    return a == null ? b == null : a.equals(b);
                }
            };

    @NonNull
    @Override
    public LagerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new LagerViewHolder(ItemLagerBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
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

            binding.textGesamt.setText(context.getResources().getQuantityString(
                    R.plurals.lager_artikel_anzahl, entry.artikel, entry.artikel));
            zaehler(binding.textVorhanden, entry.vorhanden(), R.string.bestand_vorhanden,
                    R.color.status_vorhanden);
            zaehler(binding.textVerliehen, entry.verliehen, R.string.bestand_verliehen,
                    R.color.status_verliehen);
            zaehler(binding.textVerloren, entry.verloren, R.string.bestand_verloren,
                    R.color.status_verloren);

            binding.getRoot().setOnClickListener(v -> listener.onLagerClick(entry.lager));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onLagerLongClick(entry.lager);
                return true;
            });
        }

        private void zaehler(TextView view, int anzahl, int formatRes, int farbeRes) {
            Context context = view.getContext();
            view.setText(context.getString(formatRes, anzahl));
            view.setTextColor(ContextCompat.getColor(context, farbeRes));
            view.setVisibility(anzahl > 0 ? View.VISIBLE : View.GONE);
        }
    }
}
