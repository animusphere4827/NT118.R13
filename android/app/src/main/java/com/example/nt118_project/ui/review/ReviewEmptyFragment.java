package com.example.nt118_project.ui.review;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.ui.ScreenFragment;

/** Chưa có bộ thẻ: nút "Tạo bộ thẻ" tạo bộ đầu tiên rồi mở bộ đó để thêm thẻ. */
public class ReviewEmptyFragment extends ScreenFragment {

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        NavController nav = NavHostFragment.findNavController(this);
        view.findViewById(R.id.btnCreateDeck).setOnClickListener(v ->
                DeckDialogs.askName(requireContext(), R.string.deck_new_title, null, name ->
                        App.container().deckRepository.createDeck(name, "", r -> {
                            if (!isAdded()) {
                                return;
                            }
                            if (r.isSuccess() && r.data != null) {
                                // Thay màn trống bằng tab bộ thẻ rồi mở bộ vừa tạo
                                nav.navigate(R.id.review_decks, null,
                                        new NavOptions.Builder().setPopUpTo(R.id.review_empty, true).build());
                                nav.navigate(R.id.deck_detail, ReviewArgs.deck(r.data.id, name));
                            } else {
                                Toast.makeText(requireContext(), r.code == 409
                                        ? getString(R.string.error_deck_exists) : r.message, Toast.LENGTH_LONG).show();
                            }
                        })));
    }
}
