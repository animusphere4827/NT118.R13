package com.example.nt118_project.ui.review;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.databinding.FragmentDeckDetailBinding;

import java.util.Collections;
import java.util.List;

/** Chi tiết bộ thẻ: thống kê, danh sách thẻ, ghim thẻ, thêm thẻ, đổi tên/xóa bộ, bắt đầu ôn. */
public class DeckDetailFragment extends Fragment {

    private FragmentDeckDetailBinding binding;
    private DeckDetailViewModel viewModel;
    private long deckId;
    private String deckName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDeckDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        Bundle args = requireArguments();
        deckId = args.getLong(ReviewArgs.DECK_ID);
        deckName = args.getString(ReviewArgs.DECK_NAME, "");
        viewModel = new ViewModelProvider(this).get(DeckDetailViewModel.class);
        viewModel.init(deckId, deckName);

        CardRowAdapter adapter = new CardRowAdapter(new CardRowAdapter.Listener() {
            @Override
            public void onOpen(CardDto card) {
                nav.navigate(R.id.card_edit, ReviewArgs.card(deckId, deckName, card));
            }

            @Override
            public void onTogglePin(CardDto card) {
                viewModel.togglePin(card);
            }
        });
        binding.rvCards.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCards.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnMenu.setOnClickListener(this::showMenu);
        binding.btnAddCard.setOnClickListener(v -> nav.navigate(R.id.card_edit, ReviewArgs.card(deckId, deckName, null)));
        binding.btnStartReview.setOnClickListener(v ->
                nav.navigate(R.id.flashcard_front, ReviewArgs.deck(deckId, deckName)));

        viewModel.name().observe(getViewLifecycleOwner(), n -> {
            deckName = n;
            binding.txtTitle.setText(n);
        });
        viewModel.cards().observe(getViewLifecycleOwner(), state -> render(state, adapter));
        viewModel.message().observe(getViewLifecycleOwner(), e -> {
            String m = e.consume();
            if (m != null) {
                Toast.makeText(requireContext(), m, Toast.LENGTH_LONG).show();
            }
        });
        viewModel.deleted().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() != null) {
                Toast.makeText(requireContext(), R.string.deck_deleted, Toast.LENGTH_SHORT).show();
                nav.popBackStack();
            }
        });
        viewModel.load();
    }

    private void render(@Nullable Resource<List<CardDto>> state, CardRowAdapter adapter) {
        if (state == null) {
            return;
        }
        if (state.isLoading()) {
            showStatus(getString(R.string.loading));
            return;
        }
        if (state.isError()) {
            showStatus(state.message);
            return;
        }
        List<CardDto> cards = state.data != null ? state.data : Collections.emptyList();
        adapter.submitList(cards);
        int due = 0;
        int mastered = 0;
        for (CardDto c : cards) {
            if (c.isDue()) due++;
            if (c.isMastered()) mastered++;
        }
        int total = cards.size();
        int pct = total == 0 ? 0 : Math.round(100f * mastered / total);
        binding.txtTotal.setText(String.valueOf(total));
        binding.txtMastered.setText(String.valueOf(mastered));
        binding.txtDue.setText(String.valueOf(due));
        binding.progressMastered.setProgress(pct);
        binding.txtMasteredPct.setText(getString(R.string.deck_mastered_pct, pct));
        binding.txtSubtitle.setText(getString(R.string.deck_subtitle, total, due));
        showStatus(total == 0 ? getString(R.string.deck_no_cards) : null);
        binding.btnStartReview.setEnabled(total > 0);
        binding.btnStartReview.setAlpha(total > 0 ? 1f : 0.5f);
    }

    private void showStatus(@Nullable String message) {
        binding.txtStatus.setVisibility(message == null ? View.GONE : View.VISIBLE);
        binding.txtStatus.setText(message);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.deck_add_card);
        menu.getMenu().add(0, 1, 1, R.string.deck_rename);
        menu.getMenu().add(0, 2, 2, R.string.deck_delete);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.card_edit, ReviewArgs.card(deckId, deckName, null));
                    break;
                case 1:
                    DeckDialogs.askName(requireContext(), R.string.deck_rename, deckName,
                            n -> viewModel.rename(n, getString(R.string.error_deck_exists)));
                    break;
                default:
                    int count = binding.rvCards.getAdapter() != null ? binding.rvCards.getAdapter().getItemCount() : 0;
                    DeckDialogs.confirm(requireContext(), R.string.deck_delete,
                            getString(R.string.deck_delete_confirm, deckName, count), viewModel::deleteDeck);
                    break;
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
