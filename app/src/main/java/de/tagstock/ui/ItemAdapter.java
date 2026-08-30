package de.tagstock.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.Map;

import de.tagstock.R;
import de.tagstock.data.ItemWithState;
import de.tagstock.databinding.ItemArtikelBinding;
import de.tagstock.util.FotoLader;
import de.tagstock.util.Formatter;

/** Zeigt Artikel mit Foto, Codes, Bestand je Zustand und Statusplakette. */
public class ItemAdapter extends ListAdapter<ItemWithState, ItemAdapter.ItemViewHolder> {

    public interface Listener {
        void onItemClick(ItemWithState item);

        void onItemMenu(View anchor, ItemWithState item);
    }

    private final Listener listener;
    /** Nur in der lageruebergreifenden Suche gefuellt. */
    private final Map<Long, String> lagerNamen = new HashMap<>();

    public ItemAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Blendet je Treffer das Lager ein - fuer die Suche ueber alle Lager. */
    public void setLagerNamen(Map<Long, String> namen) {
        lagerNamen.clear();
        lagerNamen.putAll(namen);
        notifyDataSetChanged();
    }

    private static final DiffUtil.ItemCallback<ItemWithState> DIFF =
            new DiffUtil.ItemCallback<ItemWithState>() {
                @Override
                public boolean areItemsTheSame(@NonNull ItemWithState a, @NonNull ItemWithState b) {
                    return a.item.id == b.item.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull ItemWithState a, @NonNull ItemWithState b) {
                    return a.item.geaendertAm == b.item.geaendertAm
                            && a.item.name.equals(b.item.name)
                            && a.item.menge == b.item.menge
                            && a.item.mengeVerloren == b.item.mengeVerloren
                            && a.item.lagerId == b.item.lagerId
                            && a.verliehen == b.verliehen
                            && a.codes.size() == b.codes.size()
                            && gleich(a.item.fotoPfad, b.item.fotoPfad);
                }

                private boolean gleich(String a, String b) {
                    return a == null ? b == null : a.equals(b);
                }
            };

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ItemViewHolder(ItemArtikelBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        holder.bind(getItem(position), listener, lagerNamen);
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        private final ItemArtikelBinding binding;

        ItemViewHolder(ItemArtikelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ItemWithState state, Listener listener, Map<Long, String> lagerNamen) {
            android.content.Context context = binding.getRoot().getContext();
            binding.textName.setText(state.item.name);

            FotoLader.laden(binding.imageFoto, state.item.fotoPfad, R.drawable.ic_lager);

            String code = Formatter.codeText(context, state);
            binding.textCode.setVisibility(code == null ? View.GONE : View.VISIBLE);
            if (code != null) {
                binding.textCode.setText(code);
            }

            String bestand = Formatter.bestandText(context, state);
            String lagerName = lagerNamen.get(state.item.lagerId);
            binding.textDetails.setText(lagerName == null
                    ? bestand : lagerName + " · " + bestand);

            Formatter.bindStatusPill(binding.textStatus, state.hauptStatus());

            binding.getRoot().setOnClickListener(v -> listener.onItemClick(state));
            binding.buttonMenu.setOnClickListener(v -> listener.onItemMenu(v, state));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onItemMenu(binding.buttonMenu, state);
                return true;
            });
        }
    }
}
