package com.example.nt118_project.ui.review;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.common.Speaker;
import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.databinding.FragmentFlashcardBinding;
import com.example.nt118_project.ui.profile.AvatarBinder;

import java.util.Locale;

/**
 * Lật thẻ: chạm để lật (animation quanh trục Y), tự đọc từ khi lật sang mặt sau,
 * Play/Pause tự lật theo chu kỳ, vuốt phải = Đã thuộc, vuốt trái = Ôn lại (POST /learn/cards/{id}/review).
 */
public class FlashcardFragment extends Fragment {

    private static final long FLIP_HALF_MS = 140;
    private static final long AUTO_FRONT_MS = 3000;
    private static final long AUTO_BACK_MS = 4000;

    private FragmentFlashcardBinding binding;
    private FlashcardViewModel viewModel;
    private Speaker speaker;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStep = this::autoStep;
    /** Mặt đang hiển thị trên màn hình, để chỉ chạy animation khi thật sự đổi mặt. */
    private boolean displayedBack;
    private long displayedCardId = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        speaker = new Speaker(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFlashcardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(FlashcardViewModel.class);
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        viewModel.start(args.getLong(ReviewArgs.DECK_ID, 0), args.getString(ReviewArgs.DECK_NAME));
        displayedBack = false;
        displayedCardId = -1;

        float density = getResources().getDisplayMetrics().density;
        binding.cardContainer.setCameraDistance(8000 * density);

        binding.btnBack.setOnClickListener(v -> close());
        binding.btnClose.setOnClickListener(v -> close());
        binding.btnFinish.setOnClickListener(v -> close());
        binding.btnFlip.setOnClickListener(v -> viewModel.flip());
        binding.btnFlipIcon.setOnClickListener(v -> viewModel.flip());
        binding.btnAgain.setOnClickListener(v -> grade(FlashcardViewModel.QUALITY_AGAIN));
        binding.btnKnown.setOnClickListener(v -> grade(FlashcardViewModel.QUALITY_KNOWN));
        binding.btnSpeakFront.setOnClickListener(v -> speakWord());
        binding.btnSpeakBack.setOnClickListener(v -> speakWord());
        binding.txtExample.setOnClickListener(v -> speaker.speak(binding.txtExample.getText().toString()));
        binding.btnAutoPlay.setOnClickListener(v ->
                viewModel.setAutoPlay(!Boolean.TRUE.equals(viewModel.autoPlay().getValue())));
        attachSwipe(binding.faceFront);
        attachSwipe(binding.faceBackContent);

        viewModel.current().observe(getViewLifecycleOwner(), this::renderCard);
        viewModel.showBack().observe(getViewLifecycleOwner(), back -> showFace(Boolean.TRUE.equals(back)));
        viewModel.autoPlay().observe(getViewLifecycleOwner(), on -> {
            binding.btnAutoPlay.setImageResource(Boolean.TRUE.equals(on) ? R.drawable.ic_pause : R.drawable.ic_play);
            binding.btnAutoPlay.setContentDescription(getString(Boolean.TRUE.equals(on)
                    ? R.string.flash_autoplay_pause : R.string.flash_autoplay));
            scheduleAuto();
        });
        viewModel.message().observe(getViewLifecycleOwner(), e -> {
            String m = e.consume();
            if (m != null) {
                Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderCard(@Nullable Resource<CardDto> state) {
        if (state == null) {
            return;
        }
        if (state.isLoading()) {
            binding.txtTitle.setText(R.string.loading);
            setSessionVisible(false);
            return;
        }
        binding.txtTitle.setText(viewModel.deckName());
        if (state.isError()) {
            if (state.code == FlashcardViewModel.NO_DECKS) {
                showNoDecks();
            } else {
                showDone(state.code == 404 ? getString(R.string.flash_empty) : state.message, false);
            }
            return;
        }
        CardDto card = state.data;
        if (card == null) {
            int known = viewModel.knownCount();
            showDone(getString(R.string.flash_done_desc, known, viewModel.againCount()), true);
            return;
        }
        setSessionVisible(true);
        binding.txtCounter.setText(String.format(Locale.ROOT, "%d / %d", viewModel.position(), viewModel.queueSize()));
        binding.progress.setProgress(100 * (viewModel.position() - 1) / Math.max(1, viewModel.queueSize()));
        binding.txtRemaining.setText(getString(R.string.flash_remaining, viewModel.remaining()));
        binding.txtDeckLabel.setText(viewModel.deckName().toUpperCase(Locale.ROOT));
        ImageViewCompat.setImageTintList(binding.imgPinned, ColorStateList.valueOf(ContextCompat.getColor(
                requireContext(), card.isPinned ? R.color.app_orange : R.color.app_border)));

        binding.txtFrontWord.setText(card.frontText);
        binding.txtBackWord.setText(card.frontText);
        setOrHide(binding.txtFrontIpa, card.ipa);
        setOrHide(binding.txtBackIpa, card.ipa);
        binding.txtMeaning.setText(card.backText);
        setOrHide(binding.txtExample, card.exampleSentence);
        setOrHide(binding.txtNote, card.note);
        binding.dividerExample.setVisibility(empty(card.exampleSentence) && empty(card.note) ? View.GONE : View.VISIBLE);
        if (AvatarBinder.isHttpUrl(card.imageUrl)) {
            binding.imgCard.setVisibility(View.VISIBLE);
            Glide.with(binding.imgCard).load(card.imageUrl).into(binding.imgCard);
        } else {
            Glide.with(binding.imgCard).clear(binding.imgCard);
            binding.imgCard.setVisibility(View.GONE);
        }
        if (card.id != displayedCardId) {
            // Thẻ mới luôn bắt đầu ở mặt trước, không cần animation
            boolean restoreBack = displayedCardId == -1 && Boolean.TRUE.equals(viewModel.showBack().getValue());
            displayedCardId = card.id;
            binding.cardContainer.animate().cancel();
            binding.cardContainer.animate().setListener(null);
            binding.cardContainer.setTranslationX(0);
            binding.cardContainer.setRotation(0);
            binding.cardContainer.setRotationY(0);
            binding.cardContainer.setAlpha(1f);
            // Xoay màn hình thì giữ mặt đang xem, còn thẻ mới luôn là mặt trước
            applyFace(restoreBack);
        }
        scheduleAuto();
    }

    private void setSessionVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        binding.cardContainer.setVisibility(v);
        binding.progress.setVisibility(v);
        binding.txtCounter.setVisibility(v);
        binding.txtRemaining.setVisibility(v);
        binding.btnFlipIcon.setVisibility(v);
        binding.btnAutoPlay.setVisibility(v);
        if (!visible) {
            binding.groupFrontActions.setVisibility(View.GONE);
            binding.groupBackActions.setVisibility(View.GONE);
        }
        binding.groupDone.setVisibility(View.GONE);
        binding.btnFinish.setVisibility(View.GONE);
    }

    private void showDone(String description, boolean success) {
        handler.removeCallbacks(autoStep);
        setSessionVisible(false);
        binding.groupDone.setVisibility(View.VISIBLE);
        binding.btnFinish.setVisibility(View.VISIBLE);
        binding.txtDoneTitle.setText(success ? R.string.flash_done_title : R.string.flash_nothing_title);
        binding.txtDoneTitle.setTextColor(ContextCompat.getColor(requireContext(),
                success ? R.color.app_success : R.color.app_text));
        binding.groupDone.setBackgroundResource(success ? R.drawable.bg_panel_ok : R.drawable.bg_panel);
        binding.txtDoneDesc.setText(description);
        binding.txtMode.setText(success ? R.string.flash_mode_done : R.string.flashcard_front_04);
    }

    /** Chưa có bộ thẻ nào: hướng sang Từ điển để tạo thẻ đầu tiên thay cho nút "Về bộ thẻ". */
    private void showNoDecks() {
        showDone(getString(R.string.flash_no_deck), false);
        binding.txtDoneTitle.setText(R.string.flash_no_deck_title);
        binding.btnFinish.setText(R.string.flash_go_dictionary);
        binding.btnFinish.setOnClickListener(v -> NavHostFragment.findNavController(this).navigate(R.id.dictionary_search,
                null, new NavOptions.Builder().setPopUpTo(R.id.flashcard_front, true).build()));
    }

    /** Đổi mặt có animation: quay nửa vòng tới 90 độ, đổi nội dung, quay tiếp về 0. */
    private void showFace(boolean back) {
        if (back == displayedBack || binding.cardContainer.getVisibility() != View.VISIBLE) {
            applyFace(back);
            return;
        }
        float direction = back ? 1f : -1f;
        binding.cardContainer.animate().cancel();
        binding.cardContainer.animate()
                .rotationY(90f * direction)
                .setDuration(FLIP_HALF_MS)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (binding == null) {
                            return;
                        }
                        binding.cardContainer.animate().setListener(null);
                        applyFace(back);
                        binding.cardContainer.setRotationY(-90f * direction);
                        binding.cardContainer.animate().rotationY(0f).setDuration(FLIP_HALF_MS).start();
                        if (back) {
                            // Tự đọc từ khi lật sang mặt sau
                            speakWord();
                        }
                    }
                })
                .start();
    }

    private void applyFace(boolean back) {
        displayedBack = back;
        binding.faceFront.setVisibility(back ? View.GONE : View.VISIBLE);
        binding.faceBack.setVisibility(back ? View.VISIBLE : View.GONE);
        binding.groupFrontActions.setVisibility(back ? View.GONE : View.VISIBLE);
        binding.groupBackActions.setVisibility(back ? View.VISIBLE : View.GONE);
        binding.txtMode.setText(back ? R.string.flashcard_back_05
                : viewModel.isFreePractice() ? R.string.flash_mode_free : R.string.flash_mode_due);
        if (back) {
            binding.faceBack.scrollTo(0, 0);
        }
        scheduleAuto();
    }

    private void grade(int quality) {
        handler.removeCallbacks(autoStep);
        viewModel.grade(quality);
    }

    private void speakWord() {
        speaker.warnIfUnavailable();
        speaker.speak(binding.txtFrontWord.getText().toString());
    }

    // ---------- Tự lật ----------

    private void scheduleAuto() {
        handler.removeCallbacks(autoStep);
        if (binding != null && Boolean.TRUE.equals(viewModel.autoPlay().getValue()) && viewModel.card() != null) {
            handler.postDelayed(autoStep, displayedBack ? AUTO_BACK_MS : AUTO_FRONT_MS);
        }
    }

    private void autoStep() {
        if (binding == null || viewModel.card() == null) {
            return;
        }
        if (displayedBack) {
            viewModel.skip();
        } else {
            viewModel.flip();
        }
    }

    // ---------- Vuốt để chấm ----------

    /** Kéo ngang thẻ: thả quá 1/3 chiều rộng thì chấm (phải = Đã thuộc, trái = Ôn lại); chạm nhẹ thì lật. */
    @SuppressLint("ClickableViewAccessibility")
    private void attachSwipe(View face) {
        int touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
        face.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                View card = binding.cardContainer;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        if (!dragging && Math.abs(dx) > touchSlop) {
                            dragging = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            handler.removeCallbacks(autoStep);
                        }
                        if (dragging) {
                            card.setTranslationX(dx);
                            card.setRotation(dx / card.getWidth() * 12f);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            viewModel.flip();
                            return true;
                        }
                        float moved = e.getRawX() - downX;
                        if (Math.abs(moved) > card.getWidth() / 3f) {
                            boolean right = moved > 0;
                            card.animate().translationX(right ? card.getWidth() * 1.5f : -card.getWidth() * 1.5f)
                                    .alpha(0f).setDuration(180)
                                    .withEndAction(() -> {
                                        if (binding != null) {
                                            grade(right ? FlashcardViewModel.QUALITY_KNOWN : FlashcardViewModel.QUALITY_AGAIN);
                                        }
                                    }).start();
                        } else {
                            card.animate().translationX(0).rotation(0).setDuration(150).start();
                            scheduleAuto();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        card.animate().translationX(0).rotation(0).setDuration(150).start();
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void close() {
        NavHostFragment.findNavController(this).popBackStack();
    }

    private static void setOrHide(TextView view, @Nullable String text) {
        view.setVisibility(empty(text) ? View.GONE : View.VISIBLE);
        view.setText(text);
    }

    private static boolean empty(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(autoStep);
        speaker.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            scheduleAuto();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(autoStep);
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        speaker.shutdown();
    }
}
