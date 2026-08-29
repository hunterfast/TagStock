package de.tagstock.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.tagstock.data.Item;
import de.tagstock.databinding.ItemArtikelBinding;
import de.tagstock.util.Formatter;

/** Zeigt die Artikel eines Lagers mit Code und Status. */
public class ItemAdapter extends ListAdapter<Item, ItemAdapter.ItemViewHolder> {

    public interface Listener {
        void onItemClick(Item item);

        void onItemMenu(View anchor, Item item);
    }

    private final Listener listener;

    public ItemAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<Item>() {
        @Override
        public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
            return a.geaendertAm == b.geaendertAm
                    && a.name.equals(b.name)
                    && a.status == b.status
                    && a.menge == b.menge
                    && a.lagerId == b.lagerId;
        }
    };

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemArtikelBinding binding = ItemArtikelBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ItemViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        private final ItemArtikelBinding binding;

        ItemViewHolder(ItemArtikelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Item item, Listener listener) {
            binding.textName.setText(item.name);

            String code = Formatter.codeLine(binding.getRoot().getContext(), item);
            if (code == null) {
                binding.textCode.setVisibility(View.GONE);
            } else {
                binding.textCode.setVisibility(View.VISIBLE);
                binding.textCode.setText(code);
            }

            binding.textDetails.setText(Formatter.detailLine(binding.getRoot().getContext(), item));
            Formatter.bindStatusPill(binding.textStatus, item.status);

            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.buttonMenu.setOnClickListener(v -> listener.onItemMenu(v, item));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onItemMenu(binding.buttonMenu, item);
                return true;
            });
        }
    }
}
