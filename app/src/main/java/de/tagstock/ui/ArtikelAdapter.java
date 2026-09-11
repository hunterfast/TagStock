package de.tagstock.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /**
     * Kennung -> Name des Behaelters. In der Zeile soll "in Ikea-Box blau"
     * stehen und nicht die nackte Kennung; der Behaelter selbst kann dabei
     * durchaus weggefiltert sein.
     */
    private Map<String, String> behaelterNamen = new HashMap<>();

    public ArtikelAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Namen der Behaelter nachreichen - kommt aus der ungefilterten Liste. */
    public void behaelterKennen(@Nullable List<Artikel> alle) {
        Map<String, String> namen = new HashMap<>();
        if (alle != null) {
            for (Artikel artikel : alle) {
                if (artikel.istBehaelter && artikel.rfidUid != null
                        && !artikel.rfidUid.isEmpty()) {
                    namen.put(artikel.rfidUid, artikel.name);
                }
            }
        }
        if (!namen.equals(behaelterNamen)) {
            behaelterNamen = namen;
            notifyDataSetChanged();
        }
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
                ausgewaehlt.contains(getItem(position).id), behaelterNamen);
    }

    static class ArtikelHolder extends RecyclerView.ViewHolder {

        private final ItemArtikelBinding binding;

        ArtikelHolder(ItemArtikelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Artikel artikel, Listener listener, boolean auswahlModus, boolean ausgewaehlt,
                  Map<String, String> behaelterNamen) {
            Context context = binding.getRoot().getContext();
            // Behaelter sind auf einen Blick zu erkennen - sie sind ja keine
            // Gegenstaende, sondern Orte.
            binding.textName.setText(artikel.istBehaelter
                    ? context.getString(R.string.behaelter_zeichen, artikel.name) : artikel.name);

            FotoLader.laden(binding.imageFoto, artikel.fotoPfad, artikel.bildUrl, R.drawable.ic_artikel);
            Formatter.statusPlakette(binding.textStatus, artikel.status);

            String zeile = Formatter.zeile(context, artikel,
                    artikel.behaelterKennung == null ? null
                            : behaelterNamen.get(artikel.behaelterKennung));
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
