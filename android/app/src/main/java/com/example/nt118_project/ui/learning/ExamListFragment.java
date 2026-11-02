package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.databinding.FragmentExamListBinding;
import com.example.nt118_project.databinding.ItemExamCardBinding;
import com.example.nt118_project.learning.StudyApi;
import com.example.nt118_project.ui.common.BottomNavHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;

import static com.example.nt118_project.learning.StudyJson.number;
import static com.example.nt118_project.learning.StudyJson.rows;
import static com.example.nt118_project.learning.StudyJson.value;

/** Danh sách đề thi thử: đề nên làm tiếp ở khung trên, các đề còn lại kèm kết quả gần nhất. */
public final class ExamListFragment extends Fragment {

    private FragmentExamListBinding binding;
    private StudyApi api;
    private final List<Call<?>> calls = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExamListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        api = App.container().apiClient.create(StudyApi.class);
        BottomNavHelper.bind(view, nav(), R.id.tabExam);
        binding.btnHistory.setOnClickListener(v -> nav().navigate(R.id.study_history));
        binding.panelNext.setVisibility(View.GONE);
        load();
    }

    private NavController nav() {
        return NavHostFragment.findNavController(this);
    }

    private void load() {
        binding.txtStatus.setText("");
        binding.txtStatus.setOnClickListener(null);
        Call<JsonObject> tests = api.catalog("mock_tests", 1, 100, Collections.emptyMap());
        calls.add(tests);
        App.container().apiCalls.enqueue(tests, catalog -> {
            calls.remove(tests);
            if (binding == null || tests.isCanceled()) return;
            if (!catalog.isSuccess()) {
                failed(catalog.message);
                return;
            }
            Call<JsonObject> history = api.history("mock", 1, 100, Collections.emptyMap());
            calls.add(history);
            App.container().apiCalls.enqueue(history, attempts -> {
                calls.remove(history);
                if (binding == null || history.isCanceled()) return;
                if (!attempts.isSuccess()) {
                    failed(attempts.message);
                    return;
                }
                show(rows(catalog.data, "rows"), rows(attempts.data, "rows"));
            });
        });
    }

    private void failed(String message) {
        binding.txtStatus.setText(message == null ? getString(R.string.assess_reload) : message + " · " + getString(R.string.assess_reload));
        binding.txtStatus.setOnClickListener(v -> load());
    }

    private void show(JsonArray tests, JsonArray attempts) {
        binding.examList.removeAllViews();
        binding.txtStatus.setText(tests.size() == 0 ? getString(R.string.exam_empty) : "");

        // đề đang làm dở được ưu tiên, sau đó là đề chưa làm lần nào
        JsonObject next = null;
        JsonObject nextAttempt = null;
        for (JsonElement item : tests) {
            JsonObject test = item.getAsJsonObject();
            JsonObject progress = latest(attempts, number(test, "id"), "IN_PROGRESS");
            if (progress != null) {
                next = test;
                nextAttempt = progress;
                break;
            }
        }
        if (next == null) {
            for (JsonElement item : tests) {
                JsonObject test = item.getAsJsonObject();
                if (latest(attempts, number(test, "id"), null) == null) {
                    next = test;
                    break;
                }
            }
        }
        bindNext(next, nextAttempt);
        if (next == null && tests.size() > 0) binding.txtStatus.setText(R.string.exam_all_done);

        for (JsonElement item : tests) {
            JsonObject test = item.getAsJsonObject();
            if (test == next) continue;
            bindCard(test, attempts);
        }
    }

    private void bindNext(@Nullable JsonObject test, @Nullable JsonObject attempt) {
        binding.panelNext.setVisibility(test == null ? View.GONE : View.VISIBLE);
        if (test == null) return;
        binding.txtNextBadge.setText(attempt != null ? R.string.exam_badge_continue : R.string.exam_badge_new);
        binding.txtNextTitle.setText(value(test, "title"));
        binding.txtNextCaption.setText(caption(test));
        binding.btnNextStart.setText(attempt != null ? R.string.exam_continue : R.string.exam_start);
        binding.btnNextStart.setOnClickListener(v -> open(attempt != null ? number(attempt, "id") : 0, number(test, "id")));
    }

    private void bindCard(JsonObject test, JsonArray attempts) {
        long id = number(test, "id");
        ItemExamCardBinding card = ItemExamCardBinding.inflate(getLayoutInflater(), binding.examList, true);
        card.txtTitle.setText(value(test, "title"));
        JsonObject done = latest(attempts, id, "SUBMITTED");
        JsonObject progress = latest(attempts, id, "IN_PROGRESS");
        card.imgDone.setVisibility(done != null ? View.VISIBLE : View.GONE);
        card.scoreRow.setVisibility(done != null ? View.VISIBLE : View.GONE);
        if (done != null) {
            double accuracy = accuracy(done);
            card.txtScore.setText(String.valueOf(Math.round(accuracy)));
            card.txtDate.setText(getString(R.string.exam_done_on, shortDate(value(done, "submitted_at"))));
            JsonObject before = previous(attempts, id, done);
            if (before == null) {
                card.txtTrend.setVisibility(View.GONE);
            } else {
                long change = Math.round(accuracy - accuracy(before));
                card.txtTrend.setText(getString(change >= 0 ? R.string.exam_trend_up : R.string.exam_trend_down,
                        String.valueOf(Math.abs(change))));
                card.txtTrend.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                        change >= 0 ? R.color.app_teal : R.color.app_error));
            }
        }
        card.txtMeta.setText(progress != null ? getString(R.string.exam_in_progress, caption(test))
                : done != null ? caption(test) : getString(R.string.exam_not_done, caption(test)));
        card.btnOpen.setText(progress != null ? R.string.exam_continue : done != null ? R.string.exam_view : R.string.exam_start);
        long attemptId = progress != null ? number(progress, "id") : done != null ? number(done, "id") : 0;
        View.OnClickListener click = v -> open(attemptId, id);
        card.btnOpen.setOnClickListener(click);
        card.getRoot().setOnClickListener(click);
    }

    private void open(long attemptId, long testId) {
        Bundle args = new Bundle();
        args.putString("kind", "mock");
        if (attemptId > 0) args.putLong("attemptId", attemptId);
        else args.putLong("contentId", testId);
        nav().navigate(R.id.exam_take, args);
    }

    private String caption(JsonObject test) {
        int minutes = (int) Math.max(1, number(test, "time_limit") / 60);
        return value(test, "test_format").equals("FULL")
                ? getString(R.string.exam_full_caption, minutes) : getString(R.string.exam_mini_caption, minutes);
    }

    /** Lượt mới nhất của một đề (lịch sử đã sắp mới trước), lọc theo trạng thái nếu có. */
    @Nullable
    private static JsonObject latest(JsonArray attempts, long testId, @Nullable String status) {
        for (JsonElement item : attempts) {
            JsonObject attempt = item.getAsJsonObject();
            if (number(attempt, "mock_test_id") != testId) continue;
            if (status == null || value(attempt, "status").equals(status)) return attempt;
        }
        return null;
    }

    @Nullable
    private static JsonObject previous(JsonArray attempts, long testId, JsonObject after) {
        boolean passed = false;
        for (JsonElement item : attempts) {
            JsonObject attempt = item.getAsJsonObject();
            if (attempt == after) {
                passed = true;
                continue;
            }
            if (passed && number(attempt, "mock_test_id") == testId && value(attempt, "status").equals("SUBMITTED")) return attempt;
        }
        return null;
    }

    private static double accuracy(JsonObject attempt) {
        JsonElement value = attempt.get("accuracy");
        return value == null || value.isJsonNull() ? 0 : value.getAsDouble();
    }

    private static String shortDate(String iso) {
        try {
            return new SimpleDateFormat("dd/MM", Locale.ROOT).format(
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US).parse(iso));
        } catch (ParseException | NullPointerException e) {
            return "";
        }
    }

    @Override
    public void onDestroyView() {
        for (Call<?> call : calls) call.cancel();
        calls.clear();
        binding = null;
        super.onDestroyView();
    }
}
