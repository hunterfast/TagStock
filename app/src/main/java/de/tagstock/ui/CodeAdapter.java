package de.tagstock.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import de.tagstock.data.Code;
import de.tagstock.databinding.ItemCodeBinding;
import de.tagstock.util.Formatter;

/** Liste der Codes eines Artikels. */
public class CodeAdapter extends ListAdapter<Code, CodeAdapter.CodeViewHolder> {

    public interface Listener {
        void onCodeEntfernen(Code code);
    }

    private final Listener listener;

    public CodeAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Code> DIFF = new DiffUtil.ItemCallback<Code>() {
        @Override
        public boolean areItemsTheSame(@NonNull Code a, @NonNull Code b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Code a, @NonNull Code b) {
            return a.wert.equals(b.wert) && a.typ == b.typ;
        }
    };

    @NonNull
    @Override
    public CodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CodeViewHolder(ItemCodeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CodeViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class CodeViewHolder extends RecyclerView.ViewHolder {

        private final ItemCodeBinding binding;

        CodeViewHolder(ItemCodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Code code, Listener listener) {
            binding.textWert.setText(code.wert);
            binding.textTyp.setText(binding.getRoot().getContext().getString(code.typ.labelRes)
                    + " · " + Formatter.date(binding.getRoot().getContext(), code.erfasstAm));
            binding.buttonEntfernen.setOnClickListener(v -> listener.onCodeEntfernen(code));
        }
    }
}
