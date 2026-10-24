package com.example.nt118_project.ui.review;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.data.remote.dto.CardRequest;
import com.example.nt118_project.databinding.FragmentCardEditBinding;
import com.example.nt118_project.ui.profile.AvatarBinder;

/** Thêm hoặc sửa thẻ: mặt trước, nghĩa, ví dụ, IPA, ảnh minh họa (URL HTTP(S)), ghim. */
public class CardEditFragment extends Fragment {

    private FragmentCardEditBinding binding;
    private CardEditViewModel viewModel;
    @Nullable
    private CardDto card;
    private long deckId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable previewImage = this::showPreview;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCardEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        Bundle args = requireArguments();
        deckId = args.getLong(ReviewArgs.DECK_ID);
        card = ReviewArgs.card(args);
        viewModel = new ViewModelProvider(this).get(CardEditViewModel.class);

        binding.txtTitle.setText(card == null ? R.string.card_add_title : R.string.card_edit_title);
        binding.txtDeck.setText(args.getString(ReviewArgs.DECK_NAME, ""));
        // Chưa có API chuyển thẻ sang bộ khác nên không cho đổi bộ
        binding.imgDeckArrow.setVisibility(View.GONE);
        binding.btnMenu.setVisibility(View.INVISIBLE);
        binding.btnDelete.setVisibility(card == null ? View.GONE : View.VISIBLE);

        if (savedInstanceState == null && card != null) {
            binding.edtWord.setText(card.frontText);
            binding.edtMeaning.setText(card.backText);
            binding.edtExample.setText(card.exampleSentence);
            binding.edtIpa.setText(card.ipa);
            binding.edtImageUrl.setText(card.imageUrl);
            binding.switchPinned.setChecked(card.isPinned);
        }
        showPreview();
        binding.edtImageUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(previewImage);
                handler.postDelayed(previewImage, 600);
            }
        });

        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnDelete.setOnClickListener(v -> {
            if (card != null) {
                DeckDialogs.confirm(requireContext(), R.string.card_delete_title,
                        getString(R.string.card_delete_confirm, card.frontText), () -> viewModel.delete(card.id));
            }
        });

        viewModel.state().observe(getViewLifecycleOwner(), state -> {
            boolean loading = state != null && state.isLoading();
            binding.btnSave.setEnabled(!loading);
            binding.btnDelete.setEnabled(!loading);
            binding.btnSave.setText(loading ? R.string.profile_saving : R.string.card_edit_07);
            if (state == null || loading) {
                return;
            }
            viewModel.consume();
            if (state.isSuccess()) {
                Toast.makeText(requireContext(), Boolean.TRUE.equals(state.data)
                        ? R.string.card_saved : R.string.card_deleted, Toast.LENGTH_SHORT).show();
                nav.popBackStack();
            } else {
                Toast.makeText(requireContext(), state.code == 409
                        ? getString(R.string.card_duplicate) : state.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void save() {
        String word = text(binding.edtWord);
        String meaning = text(binding.edtMeaning);
        String imageUrl = text(binding.edtImageUrl);
        if (word.isEmpty() || word.length() > 500) {
            error(binding.edtWord, R.string.error_card_word);
            return;
        }
        if (meaning.isEmpty() || meaning.length() > 5000) {
            error(binding.edtMeaning, R.string.error_card_meaning);
            return;
        }
        if (!imageUrl.isEmpty() && !AvatarBinder.isHttpUrl(imageUrl)) {
            error(binding.edtImageUrl, R.string.error_avatar_url);
            return;
        }
        CardRequest body = card != null ? card.toRequest() : new CardRequest();
        body.frontText = word;
        body.backText = meaning;
        body.exampleSentence = text(binding.edtExample);
        body.ipa = text(binding.edtIpa);
        body.note = body.note == null ? "" : body.note;
        body.imageUrl = imageUrl.isEmpty() ? null : imageUrl;
        body.isPinned = binding.switchPinned.isChecked();
        viewModel.save(deckId, card != null ? card.id : null, body);
    }

    private void showPreview() {
        if (binding == null) {
            return;
        }
        String url = text(binding.edtImageUrl);
        if (AvatarBinder.isHttpUrl(url)) {
            binding.imgPreview.setVisibility(View.VISIBLE);
            Glide.with(binding.imgPreview).load(url).centerCrop().error(R.drawable.ic_file_text).into(binding.imgPreview);
        } else {
            Glide.with(binding.imgPreview).clear(binding.imgPreview);
            binding.imgPreview.setVisibility(View.GONE);
        }
    }

    private void error(EditText edit, int message) {
        edit.setError(getString(message));
        edit.requestFocus();
    }

    private static String text(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(previewImage);
        binding = null;
    }
}
