package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Speaker;
import com.example.nt118_project.learning.LessonRouter;
import com.example.nt118_project.learning.VocabularyLessonViewModel;
import com.example.nt118_project.learning.VocabularySession;

public final class VocabularyLessonFragment extends Fragment {
    private VocabularyLessonViewModel model;
    private Speaker speaker;
    private boolean rendering;

    public VocabularyLessonFragment() {
        super(R.layout.fragment_vocabulary_lesson);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(this).get(VocabularyLessonViewModel.class);
        speaker = new Speaker(requireContext());
        long id = 0;
        try { id = Long.parseLong(requireArguments().getString(LessonRouter.ARG_LESSON_ID, "0")); }
        catch (RuntimeException ignored) { }
        view.findViewById(R.id.vocabBack).setOnClickListener(v -> close());
        view.findViewById(R.id.vocabReload).setOnClickListener(v -> model.refresh());
        view.findViewById(R.id.vocabSkip).setOnClickListener(v -> model.skip());
        EditText input = view.findViewById(R.id.vocabAnswer);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!rendering) model.rememberAnswer(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        view.findViewById(R.id.vocabPrevious).setOnClickListener(v -> model.previousQuestion());
        view.findViewById(R.id.vocabSpeak).setOnClickListener(v -> {
            VocabularySession.Word word = model.nextWord();
            if (word != null) speaker.speak(word.word);
        });
        model.changes.observe(getViewLifecycleOwner(), ignored -> render(view));
        model.start(id);
    }

    private void render(View view) {
        rendering = true;
        TextView progress = view.findViewById(R.id.vocabProgress);
        TextView title = view.findViewById(R.id.vocabTitle);
        TextView body = view.findViewById(R.id.vocabBody);
        TextView error = view.findViewById(R.id.vocabError);
        EditText input = view.findViewById(R.id.vocabAnswer);
        Button action = view.findViewById(R.id.vocabAction);
        ProgressBar loading = view.findViewById(R.id.vocabLoading);
        View previous = view.findViewById(R.id.vocabPrevious);
        View speak = view.findViewById(R.id.vocabSpeak);
        View skip = view.findViewById(R.id.vocabSkip);
        loading.setVisibility(model.busy ? View.VISIBLE : View.GONE);
        error.setText(model.error);
        error.setVisibility(model.error == null ? View.GONE : View.VISIBLE);
        View reload = view.findViewById(R.id.vocabReload);
        reload.setVisibility(model.error == null || model.session == null ? View.GONE : View.VISIBLE);
        reload.setEnabled(!model.busy);
        action.setEnabled(!model.busy);
        input.setEnabled(!model.busy);
        input.setVisibility(View.GONE);
        previous.setVisibility(View.GONE);
        speak.setVisibility(View.GONE);
        skip.setVisibility(View.GONE);
        VocabularySession session = model.session;
        if (session == null) {
            progress.setText("");
            title.setText(R.string.vocab_lesson_title);
            body.setText(R.string.vocab_loading_hint);
            action.setText(R.string.vocab_retry);
            action.setOnClickListener(v -> model.refresh());
        } else if (model.result != null) {
            VocabularySession.Result result = model.result;
            progress.setText(getString(R.string.vocab_result_count, result.correct_count, result.total));
            title.setText(result.passed ? R.string.vocab_passed : R.string.vocab_not_passed);
            StringBuilder feedback = new StringBuilder(getString(R.string.vocab_required_count, result.required_correct));
            if (result.review != null) for (VocabularySession.Review item : result.review) {
                feedback.append("\n\n").append(item.is_correct ? "✓ " : "• ")
                        .append(item.prompt).append(" → ").append(item.correct_answer);
                if (!item.is_correct) feedback.append("\n").append(getString(R.string.vocab_your_answer, item.answer));
            }
            feedback.append("\n\n").append(getString(R.string.vocab_review_hint));
            body.setText(feedback);
            action.setText(result.lesson_completed ? R.string.vocab_finish : R.string.vocab_continue);
            action.setOnClickListener(v -> { if (result.lesson_completed) close(); else model.refresh(); });
        } else if (model.review != null) {
            VocabularySession.WordReview review = model.review;
            progress.setText(getString(R.string.vocab_review_page, review.page));
            title.setText(R.string.vocab_review_title);
            StringBuilder text = new StringBuilder();
            for (VocabularySession.Word word : review.rows) {
                if (text.length() > 0) text.append("\n\n");
                text.append(word.word).append(" ").append(text(word.ipa)).append("\n")
                        .append(word.meaning_vi).append("\n").append(text(word.example_sentence));
            }
            body.setText(text);
            previous.setVisibility(review.page > 1 ? View.VISIBLE : View.GONE);
            previous.setEnabled(!model.busy);
            previous.setOnClickListener(v -> model.review(review.page - 1));
            boolean hasNext = review.page * review.limit < review.total;
            action.setText(hasNext ? R.string.vocab_review_next : R.string.vocab_finish);
            action.setOnClickListener(v -> { if (hasNext) model.review(review.page + 1); else close(); });
        } else if ("COMPLETED".equals(session.state)) {
            progress.setText(getString(R.string.vocab_words_count, session.passed_words, session.total_words));
            title.setText(R.string.vocab_lesson_done);
            body.setText(R.string.vocab_review_hint);
            action.setText(R.string.vocab_review_title);
            action.setOnClickListener(v -> model.review(1));
        } else if (model.check != null) {
            VocabularySession.Question question = model.check.questions.get(model.questionIndex);
            progress.setText(getString(R.string.vocab_question_count, model.questionIndex + 1, model.check.total));
            title.setText(question.prompt);
            body.setText(getString(R.string.vocab_question_hint,
                    text(question.part_of_speech), text(question.hint)));
            input.setVisibility(View.VISIBLE);
            String answer = model.currentAnswer();
            if (!input.getText().toString().equals(answer)) { input.setText(answer); input.setSelection(answer.length()); }
            previous.setVisibility(model.questionIndex > 0 ? View.VISIBLE : View.GONE);
            previous.setEnabled(!model.busy);
            previous.setOnClickListener(v -> model.previousQuestion());
            skip.setVisibility(View.VISIBLE);
            skip.setEnabled(!model.busy);
            action.setText(model.questionIndex + 1 == model.check.total ? R.string.vocab_submit : R.string.vocab_next);
            action.setOnClickListener(v -> model.answer(input.getText().toString()));
        } else {
            VocabularySession.Word word = model.nextWord();
            progress.setText(getString(R.string.vocab_batch_count, session.batch_number, session.total_batches,
                    session.passed_words, session.total_words));
            if (word == null) {
                title.setText(R.string.vocab_ready);
                body.setText(getString(R.string.vocab_check_hint, session.required_percent));
                action.setText(R.string.vocab_start_check);
                action.setOnClickListener(v -> model.beginCheck());
            } else {
                title.setText(word.word);
                body.setText(text(word.ipa) + "\n" + text(word.part_of_speech) + "\n\n" + text(word.meaning_vi)
                        + "\n\n" + text(word.example_sentence));
                speak.setVisibility(View.VISIBLE);
                action.setText(R.string.vocab_studied);
                action.setOnClickListener(v -> model.study());
            }
        }
        rendering = false;
    }

    private static String text(String value) { return value == null ? "" : value; }


    private void close() { NavHostFragment.findNavController(this).popBackStack(); }

    @Override
    public void onDestroyView() {
        if (speaker != null) speaker.shutdown();
        speaker = null;
        super.onDestroyView();
    }
}
