package com.example.nt118_project.ui.review;

import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.nt118_project.R;
import com.example.nt118_project.ui.common.StateView;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.DeckDto;
import com.example.nt118_project.databinding.FragmentReviewDecksBinding;
import com.example.nt118_project.ui.ScreenFragment;

import java.util.List;

/**
 * Tab Ôn tập: lưới 2 cột các bộ thẻ, ôn nhanh thẻ đến hạn, tạo bộ mới (nút +), giữ lâu một bộ để đổi tên/xóa.
 * Chưa có bộ nào thì chuyển sang màn review_empty. Thanh tab dưới vẫn nối qua {@link ScreenFragment}.
 */
public class ReviewDecksFragment extends ScreenFragment {

    private FragmentReviewDecksBinding binding;
    private ReviewDecksViewModel viewModel;
    private StateView stateView;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentReviewDecksBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(ReviewDecksViewModel.class);
        stateView = new StateView(binding.state);
        NavController nav = NavHostFragment.findNavController(this);

        DeckAdapter adapter = new DeckAdapter(new DeckAdapter.Listener() {
            @Override
            public void onOpen(DeckDto deck) {
                nav.navigate(R.id.deck_detail, ReviewArgs.deck(deck.id, deck.name));
            }

            @Override
            public void onMenu(DeckDto deck, View anchor) {
                showDeckMenu(deck, anchor);
            }
        });
        binding.rvDecks.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvDecks.setAdapter(adapter);

        binding.btnAdd.setOnClickListener(v -> DeckDialogs.askName(requireContext(), R.string.deck_new_title, null,
                name -> viewModel.create(name, getString(R.string.error_deck_exists))));
        binding.btnSort.setOnClickListener(this::showSortMenu);
        binding.btnQuickReview.setOnClickListener(v -> {
            DeckDto deck = viewModel.mostDue();
            if (deck != null) {
                nav.navigate(R.id.flashcard_front, ReviewArgs.deck(deck.id, deck.name));
            }
        });

        viewModel.decks().observe(getViewLifecycleOwner(), state -> render(state, adapter, nav));
        viewModel.message().observe(getViewLifecycleOwner(), e -> {
            String m = e.consume();
            if (m != null) {
                Toast.makeText(requireContext(), m, Toast.LENGTH_LONG).show();
            }
        });
        // Tải lại mỗi lần quay về (sau khi sửa bộ, ôn thẻ)
        viewModel.load();
    }

    private void render(@Nullable Resource<List<DeckDto>> state, DeckAdapter adapter, NavController nav) {
        if (state == null) {
            return;
        }
        if (state.isLoading()) {
            stateView.loading();
            return;
        }
        if (state.isError()) {
            stateView.error(state.message, viewModel::load);
            return;
        }
        List<DeckDto> decks = state.data;
        if (decks == null || decks.isEmpty()) {
            nav.navigate(R.id.review_empty, null,
                    new NavOptions.Builder().setPopUpTo(R.id.review_decks, true).build());
            return;
        }
        stateView.hide();
        adapter.submitList(decks);
        binding.txtDeckCount.setText(getString(R.string.deck_count, decks.size()));
        int due = viewModel.totalDue();
        binding.txtDueTotal.setText(String.valueOf(due));
        binding.txtDueHint.setText(due > 0 ? getString(R.string.review_due_hint, Math.max(1, Math.round(due * 0.4f)))
                : getString(R.string.review_no_due));
        binding.btnQuickReview.setEnabled(due > 0);
        binding.btnQuickReview.setAlpha(due > 0 ? 1f : 0.5f);
    }


    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.sort_recent);
        menu.getMenu().add(0, 1, 1, R.string.sort_name);
        menu.getMenu().add(0, 2, 2, R.string.sort_due);
        menu.setOnMenuItemClickListener(item -> {
            viewModel.setSort(ReviewDecksViewModel.Sort.values()[item.getItemId()]);
            return true;
        });
        menu.show();
    }

    private void showDeckMenu(DeckDto deck, View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.deck_rename);
        menu.getMenu().add(0, 1, 1, R.string.deck_delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                DeckDialogs.askName(requireContext(), R.string.deck_rename, deck.name,
                        name -> viewModel.rename(deck, name, getString(R.string.error_deck_exists)));
            } else {
                DeckDialogs.confirm(requireContext(), R.string.deck_delete,
                        getString(R.string.deck_delete_confirm, deck.name, deck.cardCount), () -> viewModel.delete(deck));
            }
            return true;
        });
        menu.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
