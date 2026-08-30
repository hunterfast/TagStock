package de.tagstock.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.tagstock.R;
import de.tagstock.data.Verleih;
import de.tagstock.databinding.ItemVerleihBinding;
import de.tagstock.util.Formatter;

/** Offene Ausleihen und Verleih-Historie eines Artikels. */
public class VerleihAdapter extends ListAdapter<Verleih, VerleihAdapter.VerleihViewHolder> {

    public interface Listener {
        void onZurueckgeben(Verleih verleih);

        void onEintragLoeschen(Verleih verleih);
    }

    private final Listener listener;

    public VerleihAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Verleih> DIFF = new DiffUtil.ItemCallback<Verleih>() {
        @Override
        public boolean areItemsTheSame(@NonNull Verleih a, @NonNull Verleih b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Verleih a, @NonNull Verleih b) {
            return a.person.equals(b.person) && a.menge == b.menge
                    && (a.zurueckAm == null ? b.zurueckAm == null : a.zurueckAm.equals(b.zurueckAm));
        }
    };

    @NonNull
    @Override
    public VerleihViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VerleihViewHolder(ItemVerleihBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VerleihViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class VerleihViewHolder extends RecyclerView.ViewHolder {

        private final ItemVerleihBinding binding;

        VerleihViewHolder(ItemVerleihBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Verleih verleih, Listener listener) {
            binding.textZeile.setText(Formatter.verleihZeile(
                    binding.getRoot().getContext(), verleih));
            binding.textZeile.setEnabled(verleih.istOffen());
            binding.buttonZurueck.setVisibility(verleih.istOffen() ? View.VISIBLE : View.GONE);
            binding.buttonZurueck.setOnClickListener(v -> listener.onZurueckgeben(verleih));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onEintragLoeschen(verleih);
                return true;
            });
            binding.textZeile.setAlpha(verleih.istOffen() ? 1f : 0.6f);
            binding.imageIcon.setImageResource(verleih.istOffen()
                    ? R.drawable.ic_verleih_offen : R.drawable.ic_verleih_zurueck);
        }
    }
}
