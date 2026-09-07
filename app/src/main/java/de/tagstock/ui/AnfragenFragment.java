package de.tagstock.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.tagstock.databinding.FragmentAnfragenBinding;
import de.tagstock.util.Einstellungen;

/**
 * Leih-Anfragen. Sie brauchen den TagStock-Server, weil mehrere Personen
 * beteiligt sind; ohne Serverzugang erklaert der Bereich, was zu tun ist.
 */
public class AnfragenFragment extends Fragment {

    private FragmentAnfragenBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAnfragenBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.buttonEinrichten.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EinstellungenActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        zeichnen();
    }

    private void zeichnen() {
        boolean server = Einstellungen.serverAktiv(requireContext());
        binding.gruppeOhneServer.setVisibility(server ? View.GONE : View.VISIBLE);
        binding.gruppeMitServer.setVisibility(server ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
