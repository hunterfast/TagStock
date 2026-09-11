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

import java.util.HashSet;
import java.util.Set;

import de.tagstock.R;
import de.tagstock.data.Artikel;
import de.tagstock.databinding.ItemArtikelBinding;
import de.tagstock.util.Formatter;
import de.tagstock.util.FotoLader;

/** Artikelkarte mit Foto, Status, Zusatzzeile und Auswahlmodus. */
public class ArtikelAdapter extends ListAdapter<Artikel, ArtikelAdapter.ArtikelHolder> {

    public interface Listener {
        void onArtikelClick(Artikel artikel);

        void onArtikelLongClick(Artikel artikel);
    }

    private final Listener listener;
    private final Set<Long> ausgewaehlt = new HashSet<>();
    private boolean auswahlModus;

    public ArtikelAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Artikel> DIFF = new DiffUtil.ItemCallback<Artikel>() {
        @Override
        public boolean areItemsTheSame(@NonNull Artikel a, @NonNull Artikel b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Artikel a, @NonNull Artikel b) {
            return a.gleichAnzeige(b);
        }
    };

    public void setAuswahlModus(boolean an) {
        auswahlModus = an;
        if (!an) {
            ausgewaehlt.clear();
        }
        notifyDataSetChanged();
    }

    public boolean istAuswahlModus() {
        return auswahlModus;
    }

    public void umschalten(Artikel artikel) {
        if (!ausgewaehlt.remove(artikel.id)) {
            ausgewaehlt.add(artikel.id);
        }
        notifyDataSetChanged();
    }

    public Set<Long> getAusgewaehlt() {
        return ausgewaehlt;
    }

    public void auswahlLeeren() {
        ausgewaehlt.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ArtikelHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ArtikelHolder(ItemArtikelBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ArtikelHolder holder, int position) {
        holder.bind(getItem(position), listener, auswahlModus,
                ausgewaehlt.contains(getItem(position).id));
    }

    static class ArtikelHolder extends RecyclerView.ViewHolder {

        private final ItemArtikelBinding binding;

        ArtikelHolder(ItemArtikelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Artikel artikel, Listener listener, boolean auswahlModus, boolean ausgewaehlt) {
            Context context = binding.getRoot().getContext();
            binding.textName.setText(artikel.name);

            FotoLader.laden(binding.imageFoto, artikel.fotoPfad, artikel.bildUrl, R.drawable.ic_artikel);
            Formatter.statusPlakette(binding.textStatus, artikel.status);

            String zeile = Formatter.zeile(context, artikel);
            binding.textZeile.setVisibility(zeile.isEmpty() ? View.GONE : View.VISIBLE);
            binding.textZeile.setText(zeile);

            boolean hatKennung = artikel.rfidUid != null && !artikel.rfidUid.isEmpty();
            binding.textKennung.setVisibility(hatKennung ? View.VISIBLE : View.GONE);
            binding.textKennung.setText(artikel.rfidUid);

            // Farbstreifen: vermisste Artikel stechen vor dem Status hervor.
            int streifen = artikel.istVermisst()
                    ? R.color.status_vermisst : artikel.status.farbeRes;
            binding.viewStreifen.setBackgroundColor(ContextCompat.getColor(context, streifen));

            binding.checkAuswahl.setVisibility(auswahlModus ? View.VISIBLE : View.GONE);
            binding.checkAuswahl.setChecked(ausgewaehlt);
            binding.imageChevron.setVisibility(auswahlModus ? View.GONE : View.VISIBLE);
            binding.getRoot().setChecked(ausgewaehlt);

            binding.getRoot().setOnClickListener(v -> listener.onArtikelClick(artikel));
            binding.getRoot().setOnLongClickListener(v -> {
                listener.onArtikelLongClick(artikel);
                return true;
            });
        }
    }
}
