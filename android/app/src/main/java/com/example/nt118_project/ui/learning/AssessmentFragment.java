package com.example.nt118_project.ui.learning;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.nt118_project.R;
import com.example.nt118_project.databinding.FragmentAssessmentIntroBinding;
import com.example.nt118_project.databinding.FragmentExamResultBinding;
import com.example.nt118_project.databinding.FragmentExamReviewBinding;
import com.example.nt118_project.databinding.FragmentExamTakeBinding;
import com.example.nt118_project.databinding.FragmentExamTimeupBinding;
import com.example.nt118_project.databinding.FragmentQuizMatchBinding;
import com.example.nt118_project.databinding.FragmentQuizOrderBinding;
import com.example.nt118_project.databinding.FragmentQuizPart5Binding;
import com.example.nt118_project.databinding.ItemExamOptionBinding;
import com.example.nt118_project.databinding.ItemQuizMatchRowBinding;
import com.example.nt118_project.databinding.ItemResultPartRowBinding;
import com.example.nt118_project.databinding.ItemReviewQuestionBinding;
import com.example.nt118_project.databinding.LayoutQuizFeedbackBinding;
import com.example.nt118_project.databinding.LayoutQuizHeaderBinding;
import com.example.nt118_project.databinding.LayoutQuizProgressBinding;
import com.example.nt118_project.learning.AssessmentViewModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.example.nt118_project.learning.StudyJson.number;
import static com.example.nt118_project.learning.StudyJson.rows;
import static com.example.nt118_project.learning.StudyJson.value;
import static com.example.nt118_project.learning.StudyJson.yes;

/**
 * Làm bài thi thử, luyện tập và quiz trên các layout fragment_exam_* và fragment_quiz_*.
 * Toàn bộ trạng thái bài làm (giờ, đáp án nháp, nộp bài) nằm ở AssessmentViewModel.
 */
public final class AssessmentFragment extends Fragment {

    private enum Screen { INTRO, TAKE, FILL, MATCH, ORDER, TIMEUP, RESULT, REVIEW }

    private static final int BLOCK = 10;
    private static final int PER_ROW = 5;

    private AssessmentViewModel model;
    private FrameLayout host;
    private Screen screen;
    private int screenIndex = -1;
    private Object binding;
    private AlertDialog dialog;
    private boolean conflictShown;

    /** Các trạng thái của riêng màn hình, lưu qua xoay màn. */
    private boolean review;
    private boolean onlyWrong = true;
    private int reviewFilter;
    private boolean sawInProgress;
    private boolean timeupSeen;
    private String pendingLeft;

    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (host == null) return;
            if (screen == Screen.TAKE && model.attemptId > 0 && !model.submitted()) {
                ((FragmentExamTakeBinding) binding).txtTitle.setText(titleWithTime());
                if (model.remaining() == 0) model.expire();
            }
            timer.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        host = new FrameLayout(requireContext());
        return host;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        model = new ViewModelProvider(this).get(AssessmentViewModel.class);
        if (saved != null) {
            review = saved.getBoolean("review");
            onlyWrong = saved.getBoolean("onlyWrong", true);
            reviewFilter = saved.getInt("reviewFilter");
            sawInProgress = saved.getBoolean("sawInProgress");
            timeupSeen = saved.getBoolean("timeupSeen");
            pendingLeft = saved.getString("pendingLeft");
        }
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        back();
                    }
                });
        model.changes.observe(getViewLifecycleOwner(), ignored -> render());
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        if (saved == null) model.focusQuestion(args.getLong("questionId", 0));
        model.prepare(args.getString("kind", "mock"), args.getLong("contentId", 0), args.getLong("attemptId", 0));
        timer.post(tick);
    }

    // ---------- Điều hướng ----------

    private NavController nav() {
        return NavHostFragment.findNavController(this);
    }

    private void back() {
        if (screen == Screen.REVIEW) {
            review = false;
            render();
            return;
        }
        if (model.attemptId <= 0 || model.submitted()) {
            nav().popBackStack();
            return;
        }
        dismissDialog();
        dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.assess_leave_title)
                .setMessage(R.string.assess_leave_msg)
                .setPositiveButton(R.string.assess_leave_ok, (d, w) -> {
                    model.save();
                    nav().popBackStack();
                })
                .setNegativeButton(R.string.assess_continue, null)
                .show();
    }

    private void open(int destination) {
        if (isResumed()) nav().navigate(destination, new Bundle());
    }

    /** Mở lượt làm mới của cùng nội dung, thay cho màn hiện tại. */
    private void retry() {
        JsonObject attempt = model.attempt();
        long content = number(attempt, model.kind.equals("quiz") ? "quiz_set_id"
                : model.kind.equals("practice") ? "practice_set_id" : "mock_test_id");
        if (content <= 0 || nav().getCurrentDestination() == null) return;
        Bundle args = new Bundle();
        args.putString("kind", model.kind);
        args.putLong("contentId", content);
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(nav().getCurrentDestination().getId(), true).build();
        nav().navigate(R.id.exam_take, args, options);
    }

    private void confirmSubmit() {
        int answered = answeredCount();
        dismissDialog();
        dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.assess_submit_title)
                .setMessage(getString(R.string.assess_submit_msg, answered, model.questions().size()))
                .setPositiveButton(R.string.exam_take_16, (d, w) -> model.finish())
                .setNegativeButton(R.string.assess_check_again, null)
                .show();
    }

    private void showConflict() {
        if (conflictShown) return;
        conflictShown = true;
        dismissDialog();
        dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.assess_conflict_title)
                .setMessage(R.string.assess_conflict_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.assess_use_server, (d, w) -> {
                    conflictShown = false;
                    model.resolveConflict(false);
                })
                .setNegativeButton(R.string.assess_use_local, (d, w) -> {
                    conflictShown = false;
                    model.resolveConflict(true);
                })
                .show();
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = null;
    }

    // ---------- Chọn màn ----------

    private Screen pickScreen() {
        if (model.data == null || yes(model.data, "ready") || model.questions().size() == 0) return Screen.INTRO;
        if (model.submitted()) {
            JsonObject result = result();
            if (sawInProgress && !timeupSeen && yes(result, "expired")) return Screen.TIMEUP;
            return review ? Screen.REVIEW : Screen.RESULT;
        }
        sawInProgress = true;
        if (!model.kind.equals("quiz")) return Screen.TAKE;
        String type = value(current(), "type");
        if (type.equals("MATCH")) return Screen.MATCH;
        if (type.equals("ORDER")) return Screen.ORDER;
        return Screen.FILL;
    }

    private void render() {
        if (host == null) return;
        if (model.conflict) showConflict();
        Screen next = pickScreen();
        boolean perQuestion = next == Screen.TAKE || next == Screen.FILL || next == Screen.MATCH || next == Screen.ORDER;
        int index = perQuestion ? model.index : -1;
        if (next != screen || index != screenIndex) {
            screen = next;
            screenIndex = index;
            if (perQuestion) pendingLeft = null;
            host.removeAllViews();
            binding = inflate(next);
        }
        switch (screen) {
            case INTRO: bindIntro((FragmentAssessmentIntroBinding) binding); break;
            case TAKE: bindTake((FragmentExamTakeBinding) binding); break;
            case FILL: bindFill((FragmentQuizPart5Binding) binding); break;
            case MATCH: bindMatch((FragmentQuizMatchBinding) binding); break;
            case ORDER: bindOrder((FragmentQuizOrderBinding) binding); break;
            case TIMEUP: bindTimeup((FragmentExamTimeupBinding) binding); break;
            case RESULT: bindResult((FragmentExamResultBinding) binding); break;
            case REVIEW: bindReview((FragmentExamReviewBinding) binding); break;
        }
    }

    private Object inflate(Screen value) {
        LayoutInflater inflater = getLayoutInflater();
        switch (value) {
            case TAKE: {
                FragmentExamTakeBinding b = FragmentExamTakeBinding.inflate(inflater, host, true);
                b.btnBack.setOnClickListener(v -> back());
                b.btnFlag.setOnClickListener(v -> model.choose(nullable(value(current(), "selected_label")),
                        !yes(current(), "marked_for_review")));
                b.btnBlock.setOnClickListener(v -> jump());
                b.btnPrev.setOnClickListener(v -> model.move(model.index - 1));
                b.btnNext.setOnClickListener(v -> model.move(model.index + 1));
                b.btnSubmit.setOnClickListener(v -> confirmSubmit());
                return b;
            }
            case FILL: {
                FragmentQuizPart5Binding b = FragmentQuizPart5Binding.inflate(inflater, host, true);
                quizHeader(b.header);
                JsonElement stored = model.quizDraft();
                JsonElement answer = current().get("answer");
                if (answer != null && answer.isJsonPrimitive()) b.inputAnswer.setText(answer.getAsString());
                else if (stored.isJsonPrimitive()) b.inputAnswer.setText(stored.getAsString());
                b.inputAnswer.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                    @Override public void afterTextChanged(Editable s) {
                        if (!answered(current())) model.rememberQuiz(new JsonPrimitive(s.toString()));
                    }
                });
                return b;
            }
            case MATCH: {
                FragmentQuizMatchBinding b = FragmentQuizMatchBinding.inflate(inflater, host, true);
                quizHeader(b.header);
                return b;
            }
            case ORDER: {
                FragmentQuizOrderBinding b = FragmentQuizOrderBinding.inflate(inflater, host, true);
                quizHeader(b.header);
                b.btnReset.setOnClickListener(v -> {
                    if (answered(current())) return;
                    model.rememberQuiz(orderAnswer(new ArrayList<>()));
                    render();
                });
                return b;
            }
            case TIMEUP: {
                FragmentExamTimeupBinding b = FragmentExamTimeupBinding.inflate(inflater, host, true);
                b.btnBack.setOnClickListener(v -> back());
                b.btnResult.setOnClickListener(v -> {
                    timeupSeen = true;
                    render();
                });
                return b;
            }
            case RESULT: {
                FragmentExamResultBinding b = FragmentExamResultBinding.inflate(inflater, host, true);
                b.btnBack.setOnClickListener(v -> back());
                b.btnStats.setOnClickListener(v -> open(R.id.stats));
                b.btnReview.setOnClickListener(v -> {
                    review = true;
                    render();
                });
                b.btnRetry.setOnClickListener(v -> retry());
                return b;
            }
            case REVIEW: {
                FragmentExamReviewBinding b = FragmentExamReviewBinding.inflate(inflater, host, true);
                b.btnBack.setOnClickListener(v -> back());
                b.btnExit.setOnClickListener(v -> back());
                b.btnOnlyWrong.setOnClickListener(v -> {
                    onlyWrong = !onlyWrong;
                    reviewFilter = 0;
                    render();
                });
                return b;
            }
            case INTRO:
            default: {
                FragmentAssessmentIntroBinding b = FragmentAssessmentIntroBinding.inflate(inflater, host, true);
                b.btnBack.setOnClickListener(v -> back());
                b.btnHistory.setOnClickListener(v -> open(R.id.study_history));
                return b;
            }
        }
    }

    // ---------- Màn giới thiệu, tải, lỗi ----------

    private void bindIntro(FragmentAssessmentIntroBinding b) {
        b.txtTitle.setText(kindTitle());
        boolean loading = model.loading && model.error == null;
        b.loading.setVisibility(loading ? View.VISIBLE : View.GONE);
        boolean ready = model.data != null && yes(model.data, "ready");
        b.content.setVisibility(ready ? View.VISIBLE : View.GONE);
        b.txtStatus.setText(model.error == null ? "" : model.error);
        if (ready) {
            b.txtHeading.setText(value(model.data, "title"));
            b.chipKind.setText(kindTitle());
            b.chipTime.setText(getString(R.string.assess_minutes, (int) Math.max(1, number(model.data, "time_limit") / 60)));
            String description = value(model.data, "description");
            b.txtDescription.setText(description);
            b.txtDescription.setVisibility(description.isEmpty() ? View.GONE : View.VISIBLE);
            b.txtRules.setText(model.kind.equals("quiz") ? R.string.assess_rules_quiz : R.string.assess_rules_reading);
            b.btnStart.setText(R.string.assess_start);
            b.btnStart.setOnClickListener(v -> model.start());
        } else if (model.data != null && !model.loading) {
            b.txtStatus.setText(model.error == null ? getString(R.string.assess_no_questions) : model.error);
            b.btnStart.setText(R.string.assess_open_history);
            b.btnStart.setOnClickListener(v -> open(R.id.study_history));
        } else if (model.attemptId <= 0 && model.contentId <= 0) {
            b.txtStatus.setText(R.string.assess_pick_content);
            b.btnStart.setText(R.string.assess_open_history);
            b.btnStart.setOnClickListener(v -> open(R.id.study_history));
        } else {
            b.btnStart.setText(R.string.assess_reload);
            b.btnStart.setOnClickListener(v -> model.refresh());
        }
        b.btnStart.setVisibility(loading ? View.GONE : View.VISIBLE);
        b.btnStart.setEnabled(!model.loading);
    }

    // ---------- Làm bài Reading (thi thử, luyện tập) ----------

    private void bindTake(FragmentExamTakeBinding b) {
        JsonObject q = current();
        JsonArray questions = model.questions();
        int total = questions.size();
        int answered = answeredCount();
        boolean locked = model.loading || model.submitting;
        b.txtTitle.setText(titleWithTime());
        b.btnFlag.setColorFilter(ContextCompat.getColor(requireContext(),
                yes(q, "marked_for_review") ? R.color.app_primary : R.color.app_text_secondary));
        b.btnFlag.setEnabled(!locked);
        b.txtPart.setText(getString(R.string.assess_part, (int) number(q, "part_id")));
        b.txtAnswered.setText(getString(R.string.assess_answered, answered, total));
        b.progress.setProgress(total == 0 ? 0 : answered * 100 / total);
        b.txtStatus.setText(model.error != null ? model.error
                : getString(model.saving ? R.string.assess_saving : R.string.assess_saved));

        bindPassages(b.passageBox, q);
        b.txtNumber.setText(getString(R.string.assess_number, model.index + 1));
        b.txtQuestion.setText(value(q, "question_text"));

        b.optionList.removeAllViews();
        String selected = value(q, "selected_label");
        for (JsonElement item : rows(q, "options")) {
            JsonObject option = item.getAsJsonObject();
            String label = value(option, "label");
            ItemExamOptionBinding row = ItemExamOptionBinding.inflate(getLayoutInflater(), b.optionList, true);
            row.txtLabel.setText(getString(R.string.assess_option_label, label));
            row.txtText.setText(value(option, "option_text"));
            row.getRoot().setSelected(label.equals(selected));
            row.getRoot().setEnabled(!locked);
            row.getRoot().setOnClickListener(v -> model.choose(label.equals(selected) ? null : label,
                    yes(q, "marked_for_review")));
        }

        int block = model.index / BLOCK;
        int first = block * BLOCK;
        int last = Math.min(total, first + BLOCK);
        b.txtBlock.setText(getString(R.string.assess_block, first + 1, last, total));
        b.gridBox.removeAllViews();
        LinearLayout line = null;
        for (int i = first; i < last; i++) {
            if ((i - first) % PER_ROW == 0) {
                line = new LinearLayout(requireContext());
                line.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = dp(8);
                b.gridBox.addView(line, params);
            }
            TextView cell = (TextView) getLayoutInflater().inflate(R.layout.item_exam_cell, line, false);
            JsonObject item = questions.get(i).getAsJsonObject();
            boolean isCurrent = i == model.index;
            boolean done = !value(item, "selected_label").isEmpty();
            cell.setBackgroundResource(isCurrent ? R.drawable.bg_cell_current : done ? R.drawable.bg_cell_done : R.drawable.bg_cell);
            cell.setTextColor(ContextCompat.getColor(requireContext(), isCurrent ? R.color.white : R.color.app_text));
            cell.setTypeface(null, isCurrent ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            cell.setText(yes(item, "marked_for_review") ? getString(R.string.assess_cell_marked, i + 1) : String.valueOf(i + 1));
            cell.setContentDescription(getString(R.string.assess_cell_desc, i + 1));
            if ((i - first) % PER_ROW == 0) {
                ((LinearLayout.LayoutParams) cell.getLayoutParams()).setMarginStart(0);
            }
            int target = i;
            cell.setOnClickListener(v -> model.move(target));
            line.addView(cell);
        }
        // giữ ô cuối hàng cuối đúng kích thước khi khối không đủ 5 câu
        if (line != null) {
            for (int i = line.getChildCount(); i < PER_ROW; i++) {
                View spacer = new View(requireContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1);
                params.setMarginStart(dp(8));
                line.addView(spacer, params);
            }
        }
        b.btnPrev.setVisibility(model.index > 0 ? View.VISIBLE : View.INVISIBLE);
        b.btnNext.setVisibility(model.index + 1 < total ? View.VISIBLE : View.INVISIBLE);
        b.txtLegend.setText(getString(R.string.assess_current, model.index + 1));
        b.btnSubmit.setText(model.submitting ? R.string.assess_submitting : R.string.exam_take_16);
        b.btnSubmit.setEnabled(!locked);
    }

    private void bindPassages(LinearLayout box, JsonObject q) {
        box.removeAllViews();
        JsonArray passages = rows(q, "passages");
        String instructions = value(q, "group_instructions");
        box.setVisibility(passages.size() == 0 && instructions.isEmpty() ? View.GONE : View.VISIBLE);
        if (!instructions.isEmpty()) box.addView(text(instructions, R.style.Text_Caption, 0));
        for (JsonElement item : passages) {
            JsonObject passage = item.getAsJsonObject();
            String title = value(passage, "title");
            if (!title.isEmpty()) box.addView(text(title, R.style.Text_Title, dp(8)));
            String image = value(passage, "image_url");
            if (!image.isEmpty()) {
                ImageView picture = new ImageView(requireContext());
                picture.setAdjustViewBounds(true);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
                params.topMargin = dp(8);
                box.addView(picture, params);
                Glide.with(this).load(image).fitCenter().into(picture);
            }
            TextView content = text(value(passage, "content"), R.style.Text_Body, dp(8));
            content.setTypeface(android.graphics.Typeface.SERIF);
            box.addView(content);
        }
    }

    private void jump() {
        JsonArray questions = model.questions();
        String[] names = new String[questions.size()];
        for (int i = 0; i < questions.size(); i++) {
            JsonObject q = questions.get(i).getAsJsonObject();
            String name = value(q, "selected_label").isEmpty()
                    ? getString(R.string.assess_cell_desc, i + 1)
                    : getString(R.string.assess_jump_answered, i + 1);
            names[i] = yes(q, "marked_for_review") ? getString(R.string.assess_jump_marked, name) : name;
        }
        dismissDialog();
        dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.assess_jump_title)
                .setItems(names, (d, which) -> model.move(which))
                .setNegativeButton(R.string.assess_close, null)
                .show();
    }

    // ---------- Quiz ----------

    private void quizHeader(LayoutQuizHeaderBinding header) {
        header.btnBack.setOnClickListener(v -> back());
        header.btnClose.setOnClickListener(v -> back());
    }

    private void bindQuizTop(LayoutQuizHeaderBinding header, LayoutQuizProgressBinding top, int kindRes) {
        int total = model.questions().size();
        int done = answeredCount();
        header.txtTitle.setText(value(model.attempt(), "content_title"));
        top.txtKind.setText(kindRes);
        top.txtCounter.setText(getString(R.string.assess_counter, model.index + 1, total));
        top.progress.setProgress(total == 0 ? 0 : done * 100 / total);
    }

    /** Nút chính của quiz: Kiểm tra, rồi Câu tiếp, câu cuối là Nộp bài. */
    private void bindQuizAction(TextView button, TextView status, Runnable check) {
        boolean answered = answered(current());
        boolean last = model.index + 1 >= model.questions().size();
        status.setText(model.error == null ? "" : model.error);
        if (model.submitting) {
            button.setText(R.string.assess_submitting);
        } else if (!answered) {
            button.setText(R.string.quiz_part5_09);
        } else {
            button.setText(last ? R.string.exam_take_16 : R.string.assess_next);
        }
        button.setEnabled(!model.loading && !model.submitting);
        button.setOnClickListener(v -> {
            if (!answered(current())) check.run();
            else if (model.index + 1 < model.questions().size()) model.move(model.index + 1);
            else confirmSubmit();
        });
    }

    private void bindFeedback(LayoutQuizFeedbackBinding f, JsonObject q) {
        if (!answered(q)) {
            f.getRoot().setVisibility(View.GONE);
            return;
        }
        JsonObject feedback = q.getAsJsonObject("feedback");
        boolean right = yes(feedback, "is_correct");
        f.getRoot().setVisibility(View.VISIBLE);
        f.getRoot().setBackgroundResource(right ? R.drawable.bg_panel_ok : R.drawable.bg_panel_err);
        f.txtFeedbackTitle.setText(right ? R.string.assess_correct : R.string.assess_incorrect);
        f.txtFeedbackAnswer.setText(getString(R.string.assess_answer_is, solutionText(q)));
        f.txtFeedbackExplain.setText(value(feedback, "explanation"));
    }

    private void bindFill(FragmentQuizPart5Binding b) {
        JsonObject q = current();
        JsonObject payload = payload(q);
        bindQuizTop(b.header, b.top, R.string.assess_kind_fill);
        b.txtQuestion.setText(value(payload, "text"));
        b.txtHint.setText(value(q, "prompt"));
        boolean answered = answered(q);
        b.inputAnswer.setEnabled(!answered && !model.loading && !model.submitting);
        bindFeedback(b.feedback, q);
        bindQuizAction(b.btnAction, b.txtStatus, () -> {
            String typed = b.inputAnswer.getText().toString();
            if (typed.trim().isEmpty()) {
                b.txtStatus.setText(R.string.assess_fill_empty);
                return;
            }
            model.checkQuiz(new JsonPrimitive(typed));
        });
    }

    private void bindMatch(FragmentQuizMatchBinding b) {
        JsonObject q = current();
        JsonObject payload = payload(q);
        bindQuizTop(b.header, b.top, R.string.assess_kind_match);
        b.txtPrompt.setText(value(q, "prompt"));
        JsonArray left = rows(payload, "left");
        JsonArray right = rows(payload, "right");
        boolean answered = answered(q);
        List<String[]> pairs = answered ? pairsOf(q.get("answer")) : pairsOf(model.quizDraft());
        JsonObject solution = answered ? q.getAsJsonObject("feedback").getAsJsonObject("correct_answer") : null;

        b.matchRows.removeAllViews();
        int count = Math.max(left.size(), right.size());
        for (int i = 0; i < count; i++) {
            ItemQuizMatchRowBinding row = ItemQuizMatchRowBinding.inflate(getLayoutInflater(), b.matchRows, true);
            if (i < left.size()) {
                JsonObject item = left.get(i).getAsJsonObject();
                String id = value(item, "id");
                int pair = pairIndex(pairs, id, 0);
                row.txtLeft.setText(pair < 0 ? value(item, "text") : getString(R.string.assess_pair_prefix, pair + 1, value(item, "text")));
                if (answered) {
                    boolean ok = pair >= 0 && pairs.get(pair)[1].equals(expectedRight(solution, id));
                    row.txtLeft.setBackgroundResource(ok ? R.drawable.bg_option_correct : R.drawable.bg_option_wrong);
                } else {
                    row.txtLeft.setSelected(id.equals(pendingLeft) || pair >= 0);
                    row.txtLeft.setOnClickListener(v -> tapLeft(id));
                }
            } else {
                row.txtLeft.setVisibility(View.INVISIBLE);
            }
            if (i < right.size()) {
                JsonObject item = right.get(i).getAsJsonObject();
                String id = value(item, "id");
                int pair = pairIndex(pairs, id, 1);
                row.txtRight.setText(pair < 0 ? value(item, "text") : getString(R.string.assess_pair_prefix, pair + 1, value(item, "text")));
                row.txtRight.setSelected(!answered && pair >= 0);
                if (!answered) row.txtRight.setOnClickListener(v -> tapRight(id));
            } else {
                row.txtRight.setVisibility(View.INVISIBLE);
            }
        }
        b.txtPaired.setText(getString(R.string.assess_paired, pairs.size(), left.size()));
        bindFeedback(b.feedback, q);
        bindQuizAction(b.btnAction, b.txtStatus, () -> model.checkQuiz(model.quizDraft()));
    }

    private void tapLeft(String id) {
        List<String[]> pairs = pairsOf(model.quizDraft());
        int existing = pairIndex(pairs, id, 0);
        if (existing >= 0) pairs.remove(existing);
        pendingLeft = id.equals(pendingLeft) && existing < 0 ? null : id;
        model.rememberQuiz(matchAnswer(pairs));
        render();
    }

    private void tapRight(String id) {
        List<String[]> pairs = pairsOf(model.quizDraft());
        int existing = pairIndex(pairs, id, 1);
        if (existing >= 0) pairs.remove(existing);
        if (pendingLeft != null) {
            pairs.add(new String[]{pendingLeft, id});
            pendingLeft = null;
        }
        model.rememberQuiz(matchAnswer(pairs));
        render();
    }

    private void bindOrder(FragmentQuizOrderBinding b) {
        JsonObject q = current();
        JsonArray tokens = rows(payload(q), "tokens");
        bindQuizTop(b.header, b.top, R.string.assess_kind_order);
        b.txtPrompt.setText(value(q, "prompt"));
        boolean answered = answered(q);
        List<String> order = orderOf(answered ? q.get("answer") : model.quizDraft());

        b.chosenTokens.removeAllViews();
        List<String> sentence = new ArrayList<>();
        for (String id : order) {
            String text = tokenText(tokens, id);
            sentence.add(text);
            TextView token = token(b.chosenTokens, text, R.drawable.bg_option_pick);
            if (!answered) token.setOnClickListener(v -> {
                List<String> next = orderOf(model.quizDraft());
                next.remove(id);
                model.rememberQuiz(orderAnswer(next));
                render();
            });
        }
        b.txtSentence.setText(sentence.isEmpty() ? getString(R.string.assess_order_empty) : android.text.TextUtils.join(" ", sentence));

        b.availableTokens.removeAllViews();
        for (JsonElement item : tokens) {
            JsonObject row = item.getAsJsonObject();
            String id = value(row, "id");
            if (order.contains(id)) continue;
            TextView token = token(b.availableTokens, value(row, "text"), R.drawable.bg_cell);
            token.setEnabled(!answered);
            token.setOnClickListener(v -> {
                List<String> next = orderOf(model.quizDraft());
                if (!next.contains(id)) next.add(id);
                model.rememberQuiz(orderAnswer(next));
                render();
            });
        }
        b.btnReset.setVisibility(answered ? View.GONE : View.VISIBLE);
        bindFeedback(b.feedback, q);
        bindQuizAction(b.btnAction, b.txtStatus, () -> model.checkQuiz(model.quizDraft()));
    }

    private TextView token(ViewGroup parent, String text, int background) {
        TextView token = (TextView) getLayoutInflater().inflate(R.layout.item_quiz_token, parent, false);
        token.setText(text);
        token.setBackgroundResource(background);
        parent.addView(token);
        return token;
    }

    // ---------- Hết giờ, kết quả ----------

    private void bindTimeup(FragmentExamTimeupBinding b) {
        int total = model.questions().size();
        int answered = answeredCount();
        b.txtTitle.setText(value(model.attempt(), "content_title"));
        b.txtAnswered.setText(String.valueOf(answered));
        b.txtSkipped.setText(String.valueOf(total - answered));
        b.progress.setProgress(total == 0 ? 0 : answered * 100 / total);
        b.txtTotal.setText(getString(R.string.assess_timeup_total, total, durationMinutes()));
    }

    private void bindResult(FragmentExamResultBinding b) {
        JsonObject result = result();
        boolean quiz = model.kind.equals("quiz");
        JsonArray questions = model.questions();
        int total = questions.size();
        int correct = (int) number(result, "correct_count");
        int skipped = total - answeredCount();
        int wrong = Math.max(0, total - correct - skipped);
        double accuracy = result.has("accuracy") && !result.get("accuracy").isJsonNull() ? result.get("accuracy").getAsDouble() : 0;

        b.txtTitle.setText(quiz ? getString(R.string.assess_result_title_quiz) : getString(R.string.exam_result_17));
        b.txtCaption.setText(getString(R.string.assess_result_caption, value(model.attempt(), "content_title")).toUpperCase(Locale.ROOT));
        b.ringScore.setProgress((int) Math.round(accuracy));
        b.txtScore.setText(String.valueOf(Math.round(accuracy)));
        b.txtScoreUnit.setText(R.string.assess_result_unit);
        b.txtNote.setText(quiz ? R.string.assess_result_note_quiz : R.string.assess_result_note_reading);
        b.txtCorrect.setText(String.valueOf(correct));
        b.txtWrong.setText(String.valueOf(wrong));
        b.txtSkipped.setText(String.valueOf(skipped));
        b.txtPartTitle.setText(quiz ? getString(R.string.assess_by_type) : getString(R.string.exam_result_13));

        while (b.partTable.getChildCount() > 1) b.partTable.removeViewAt(1);
        if (quiz) {
            for (String type : new String[]{"FILL_BLANK", "MATCH", "ORDER"}) addPartRow(b.partTable, QuestionTypeNames.of(type), group(type, -1));
        } else {
            for (int part = 5; part <= 7; part++) addPartRow(b.partTable, null, group(null, part));
        }
    }

    /** Đếm đúng, sai, bỏ trống của một nhóm câu: theo dạng câu quiz hoặc theo Part. */
    private int[] group(@Nullable String type, int part) {
        int[] out = new int[4];
        out[3] = part;
        for (JsonElement item : model.questions()) {
            JsonObject q = item.getAsJsonObject();
            if (type != null ? !value(q, "type").equals(type) : number(q, "part_id") != part) continue;
            out[0]++;
            if (!isAnswered(q)) out[2]++;
            else if (isCorrect(q)) out[1]++;
        }
        return out;
    }

    private void addPartRow(LinearLayout table, @Nullable String name, int[] counts) {
        if (counts[0] == 0) return;
        ItemResultPartRowBinding row = ItemResultPartRowBinding.inflate(getLayoutInflater(), table, true);
        row.txtName.setText(name != null ? getString(R.string.assess_row_name, name, counts[0])
                : getString(R.string.assess_row_name, getString(R.string.assess_part, counts[3]).replace("PART", "Part"), counts[0]));
        row.txtCorrect.setText(String.valueOf(counts[1]));
        row.txtWrong.setText(String.valueOf(counts[0] - counts[1] - counts[2]));
        row.txtSkipped.setText(String.valueOf(counts[2]));
    }

    // ---------- Lời giải ----------

    private void bindReview(FragmentExamReviewBinding b) {
        boolean quiz = model.kind.equals("quiz");
        JsonArray questions = model.questions();
        List<Integer> base = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            if (!onlyWrong || !isCorrect(questions.get(i).getAsJsonObject())) base.add(i);
        }
        b.txtCaption.setText(getString(R.string.assess_review_caption, value(model.attempt(), "content_title"),
                questions.size()).toUpperCase(Locale.ROOT));
        b.txtHeadline.setText(onlyWrong ? getString(R.string.assess_review_wrong, base.size())
                : getString(R.string.assess_review_all, base.size()));
        b.btnOnlyWrong.setText(onlyWrong ? R.string.assess_all_questions : R.string.assess_only_wrong);

        // chip: tất cả, rồi từng Part (Reading) hoặc từng dạng câu (quiz)
        b.chipRow.removeAllViews();
        addChip(b.chipRow, getString(R.string.assess_chip_all, base.size()), 0);
        if (quiz) {
            String[] types = {"FILL_BLANK", "MATCH", "ORDER"};
            for (int t = 0; t < types.length; t++) {
                int n = 0;
                for (int i : base) if (value(questions.get(i).getAsJsonObject(), "type").equals(types[t])) n++;
                if (n > 0) addChip(b.chipRow, getString(R.string.assess_row_name, QuestionTypeNames.of(types[t]), n), t + 1);
            }
        } else {
            for (int part = 5; part <= 7; part++) {
                int n = 0;
                for (int i : base) if (number(questions.get(i).getAsJsonObject(), "part_id") == part) n++;
                if (n > 0) addChip(b.chipRow, getString(R.string.assess_row_name,
                        getString(R.string.assess_part, part).replace("PART", "Part"), n), part);
            }
        }

        b.reviewList.removeAllViews();
        long lastGroup = -1;
        int shown = 0;
        for (int i : base) {
            JsonObject q = questions.get(i).getAsJsonObject();
            if (reviewFilter != 0) {
                if (quiz && !value(q, "type").equals(new String[]{"", "FILL_BLANK", "MATCH", "ORDER"}[reviewFilter])) continue;
                if (!quiz && number(q, "part_id") != reviewFilter) continue;
            }
            ItemReviewQuestionBinding item = ItemReviewQuestionBinding.inflate(getLayoutInflater(), b.reviewList, true);
            if (quiz) bindQuizReview(item, q, i);
            else {
                long group = number(q, "group_id");
                bindReadingReview(item, q, i, group > 0 && group != lastGroup);
                lastGroup = group;
            }
            shown++;
        }
        b.txtEmpty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private void addChip(LinearLayout row, String text, int filter) {
        TextView chip = new TextView(requireContext(), null, 0, R.style.Chip);
        chip.setText(text);
        boolean selected = filter == reviewFilter;
        chip.setSelected(selected);
        chip.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.app_primary : R.color.app_text_secondary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(8));
        chip.setOnClickListener(v -> {
            reviewFilter = filter;
            render();
        });
        row.addView(chip, params);
    }

    private void bindReadingReview(ItemReviewQuestionBinding item, JsonObject q, int index, boolean firstOfGroup) {
        item.txtNumber.setText(getString(R.string.assess_review_number_part, index + 1, (int) number(q, "part_id")));
        item.txtType.setText(QuestionTypeNames.of(value(q, "question_type")));
        item.txtQuestion.setText(value(q, "question_text"));

        List<String> passages = new ArrayList<>();
        List<String> translations = new ArrayList<>();
        if (firstOfGroup) {
            for (JsonElement p : rows(q, "passages")) {
                JsonObject passage = p.getAsJsonObject();
                passages.add(value(passage, "content"));
                String translation = value(passage, "translation_vi");
                if (!translation.isEmpty()) translations.add(translation);
            }
        }
        item.txtPassage.setVisibility(passages.isEmpty() ? View.GONE : View.VISIBLE);
        item.txtPassage.setText(android.text.TextUtils.join("\n\n", passages));
        item.txtTranslation.setVisibility(translations.isEmpty() ? View.GONE : View.VISIBLE);
        item.txtTranslation.setText(getString(R.string.assess_translation, android.text.TextUtils.join("\n\n", translations)));

        String selected = value(q, "selected_label");
        String correctLabel = "";
        String correctText = "";
        String selectedText = "";
        List<String> others = new ArrayList<>();
        for (JsonElement o : rows(q, "options")) {
            JsonObject option = o.getAsJsonObject();
            String label = value(option, "label");
            if (yes(option, "is_correct")) {
                correctLabel = label;
                correctText = value(option, "option_text");
            } else if (label.equals(selected)) {
                selectedText = value(option, "option_text");
            } else {
                others.add(getString(R.string.assess_option_inline, label, value(option, "option_text")));
            }
        }
        boolean right = isCorrect(q);
        item.rowChosen.setVisibility(right ? View.GONE : View.VISIBLE);
        if (selected.isEmpty()) {
            item.txtChosenLabel.setText(R.string.assess_skipped_label);
            item.txtChosenText.setText(R.string.assess_skipped);
            item.txtChosenNote.setVisibility(View.GONE);
        } else {
            item.txtChosenLabel.setText(getString(R.string.assess_option_label, selected));
            item.txtChosenText.setText(selectedText);
            item.txtChosenNote.setText(R.string.assess_you_chose);
        }
        item.txtCorrectLabel.setText(getString(R.string.assess_option_label, correctLabel));
        item.txtCorrectText.setText(correctText);
        item.txtOthers.setText(android.text.TextUtils.join(getString(R.string.assess_option_sep), others));
        item.txtOthers.setVisibility(others.isEmpty() ? View.GONE : View.VISIBLE);
        item.txtWhy.setText(getString(R.string.assess_why, correctLabel));
        item.txtExplain.setText(value(q, "explanation"));
    }

    private void bindQuizReview(ItemReviewQuestionBinding item, JsonObject q, int index) {
        item.txtNumber.setText(getString(R.string.assess_number, index + 1));
        item.txtType.setText(QuestionTypeNames.of(value(q, "type")));
        String text = value(payload(q), "text");
        item.txtPassage.setVisibility(View.GONE);
        item.txtQuestion.setText(text.isEmpty() ? value(q, "prompt") : value(q, "prompt") + "\n" + text);
        boolean right = isCorrect(q);
        item.rowChosen.setVisibility(right ? View.GONE : View.VISIBLE);
        item.txtChosenLabel.setVisibility(View.GONE);
        item.txtChosenText.setText(isAnswered(q) ? answerText(q, q.get("answer")) : getString(R.string.assess_skipped));
        item.txtChosenNote.setText(R.string.assess_you_answered);
        item.txtChosenNote.setVisibility(isAnswered(q) ? View.VISIBLE : View.GONE);
        item.txtCorrectLabel.setVisibility(View.GONE);
        item.txtCorrectText.setText(solutionText(q));
        item.txtOthers.setVisibility(View.GONE);
        item.txtWhy.setText(R.string.assess_explain);
        JsonObject feedback = q.has("feedback") && q.get("feedback").isJsonObject() ? q.getAsJsonObject("feedback") : null;
        item.txtExplain.setText(feedback == null ? "" : value(feedback, "explanation"));
        item.txtTranslation.setVisibility(View.GONE);
    }

    // ---------- Dữ liệu bài làm ----------

    private JsonObject current() {
        JsonArray questions = model.questions();
        if (questions.size() == 0) return new JsonObject();
        return questions.get(Math.max(0, Math.min(model.index, questions.size() - 1))).getAsJsonObject();
    }

    private JsonObject result() {
        JsonObject attempt = model.attempt();
        return attempt.has("result") && attempt.get("result").isJsonObject() ? attempt.getAsJsonObject("result") : new JsonObject();
    }

    private static JsonObject payload(JsonObject q) {
        return q.has("payload") && q.get("payload").isJsonObject() ? q.getAsJsonObject("payload") : new JsonObject();
    }

    private boolean answered(JsonObject q) {
        return q.has("feedback") && q.get("feedback").isJsonObject();
    }

    private boolean isAnswered(JsonObject q) {
        if (model.kind.equals("quiz")) return q.has("answer") && !q.get("answer").isJsonNull();
        return !value(q, "selected_label").isEmpty();
    }

    private boolean isCorrect(JsonObject q) {
        if (model.kind.equals("quiz")) return answered(q) && yes(q.getAsJsonObject("feedback"), "is_correct");
        return yes(q, "is_correct");
    }

    private int answeredCount() {
        int count = 0;
        for (JsonElement item : model.questions()) {
            JsonObject q = item.getAsJsonObject();
            if (model.kind.equals("quiz") ? answered(q) || isAnswered(q) : isAnswered(q)) count++;
        }
        return count;
    }

    private String kindTitle() {
        return getString(model.kind.equals("quiz") ? R.string.assess_title_quiz
                : model.kind.equals("practice") ? R.string.assess_title_practice : R.string.assess_title_mock);
    }

    private String titleWithTime() {
        long seconds = model.remaining() / 1000;
        return getString(R.string.assess_title_time, value(model.attempt(), "content_title"),
                String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60));
    }

    private int durationMinutes() {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);
            long start = format.parse(value(model.attempt(), "started_at")).getTime();
            long end = format.parse(value(model.attempt(), "deadline_at")).getTime();
            return (int) Math.max(1, Math.round((end - start) / 60000.0));
        } catch (ParseException | NullPointerException e) {
            return 0;
        }
    }

    @Nullable
    private static String nullable(String label) {
        return label.isEmpty() ? null : label;
    }

    private static String tokenText(JsonArray tokens, String id) {
        for (JsonElement item : tokens) {
            if (value(item.getAsJsonObject(), "id").equals(id)) return value(item.getAsJsonObject(), "text");
        }
        return "";
    }

    private static List<String[]> pairsOf(@Nullable JsonElement answer) {
        List<String[]> pairs = new ArrayList<>();
        if (answer == null || !answer.isJsonObject()) return pairs;
        for (JsonElement item : rows(answer.getAsJsonObject(), "pairs")) {
            JsonObject pair = item.getAsJsonObject();
            pairs.add(new String[]{value(pair, "left_id"), value(pair, "right_id")});
        }
        return pairs;
    }

    private static int pairIndex(List<String[]> pairs, String id, int side) {
        for (int i = 0; i < pairs.size(); i++) if (pairs.get(i)[side].equals(id)) return i;
        return -1;
    }

    private static JsonObject matchAnswer(List<String[]> pairs) {
        JsonArray array = new JsonArray();
        for (String[] pair : pairs) {
            JsonObject row = new JsonObject();
            row.addProperty("left_id", pair[0]);
            row.addProperty("right_id", pair[1]);
            array.add(row);
        }
        JsonObject answer = new JsonObject();
        answer.add("pairs", array);
        return answer;
    }

    private static String expectedRight(@Nullable JsonObject solution, String left) {
        if (solution == null) return "";
        for (JsonElement item : rows(solution, "pairs")) {
            JsonObject pair = item.getAsJsonObject();
            if (value(pair, "left_id").equals(left)) return value(pair, "right_id");
        }
        return "";
    }

    private static List<String> orderOf(@Nullable JsonElement answer) {
        List<String> order = new ArrayList<>();
        if (answer == null || !answer.isJsonObject()) return order;
        for (JsonElement id : rows(answer.getAsJsonObject(), "order")) order.add(id.getAsString());
        return order;
    }

    private static JsonObject orderAnswer(List<String> order) {
        JsonArray array = new JsonArray();
        for (String id : order) array.add(id);
        JsonObject answer = new JsonObject();
        answer.add("order", array);
        return answer;
    }

    /** Đáp án người học, dạng chữ để hiện ở lời giải. */
    private String answerText(JsonObject q, @Nullable JsonElement answer) {
        if (answer == null || answer.isJsonNull()) return getString(R.string.assess_no_answer);
        JsonObject payload = payload(q);
        switch (value(q, "type")) {
            case "MATCH": {
                List<String> lines = new ArrayList<>();
                for (String[] pair : pairsOf(answer)) {
                    lines.add(tokenText(rows(payload, "left"), pair[0]) + " → " + tokenText(rows(payload, "right"), pair[1]));
                }
                return android.text.TextUtils.join("\n", lines);
            }
            case "ORDER": {
                List<String> words = new ArrayList<>();
                for (String id : orderOf(answer)) words.add(tokenText(rows(payload, "tokens"), id));
                return android.text.TextUtils.join(" ", words);
            }
            default:
                return answer.isJsonPrimitive() ? answer.getAsString() : answer.toString();
        }
    }

    /** Đáp án đúng do server trả về sau khi chấm câu. */
    private String solutionText(JsonObject q) {
        if (!answered(q)) return "";
        JsonObject solution = q.getAsJsonObject("feedback").getAsJsonObject("correct_answer");
        if (solution == null) return "";
        switch (value(q, "type")) {
            case "MATCH":
                return answerText(q, solution);
            case "ORDER":
                return answerText(q, solution);
            default: {
                List<String> accepted = new ArrayList<>();
                for (JsonElement word : rows(solution, "answers")) accepted.add(word.getAsString());
                return android.text.TextUtils.join(" / ", accepted);
            }
        }
    }

    // ---------- Tiện ích view ----------

    private TextView text(String value, int style, int topMargin) {
        TextView view = new TextView(requireContext(), null, 0, style);
        view.setText(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        out.putBoolean("review", review);
        out.putBoolean("onlyWrong", onlyWrong);
        out.putInt("reviewFilter", reviewFilter);
        out.putBoolean("sawInProgress", sawInProgress);
        out.putBoolean("timeupSeen", timeupSeen);
        out.putString("pendingLeft", pendingLeft);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onPause() {
        if (model != null) model.save();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        timer.removeCallbacksAndMessages(null);
        dismissDialog();
        conflictShown = false;
        host = null;
        binding = null;
        screen = null;
        screenIndex = -1;
        super.onDestroyView();
    }
}
