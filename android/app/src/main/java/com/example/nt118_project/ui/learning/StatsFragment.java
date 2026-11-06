package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.databinding.FragmentStatsBinding;
import com.example.nt118_project.databinding.ItemStatsDayBinding;
import com.example.nt118_project.databinding.ItemStatsPartBinding;
import com.example.nt118_project.databinding.ItemWeakTopicBinding;
import com.example.nt118_project.learning.StudyApi;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import retrofit2.Call;

import static com.example.nt118_project.learning.StudyJson.number;
import static com.example.nt118_project.learning.StudyJson.rows;
import static com.example.nt118_project.learning.StudyJson.value;

/** Tiến bộ học tập từ GET /learn/analytics: hoạt động 7 ngày, tỷ lệ đúng theo Part, dạng câu cần luyện. */
public final class StatsFragment extends Fragment {

    private static final int BAR_MAX_DP = 96;

    private FragmentStatsBinding binding;
    private Call<JsonObject> pending;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnBack.setOnClickListener(v -> nav().popBackStack());
        binding.btnHistory.setOnClickListener(v -> nav().navigate(R.id.study_history));
        binding.btnMistakes.setOnClickListener(v -> nav().navigate(R.id.study_stats));
        binding.txtActivityTotal.setText("");
        binding.txtOverall.setText("");
        binding.txtScoreNote.setText("");
        load();
    }

    private NavController nav() {
        return NavHostFragment.findNavController(this);
    }

    private void load() {
        Call<JsonObject> call = App.container().apiClient.create(StudyApi.class).analytics();
        pending = call;
        binding.txtStreak.setOnClickListener(null);
        App.container().apiCalls.enqueue(call, result -> {
            if (binding == null || call.isCanceled()) return;
            pending = null;
            if (!result.isSuccess()) {
                binding.txtStreak.setText(result.message + " · " + getString(R.string.assess_reload));
                binding.txtStreak.setOnClickListener(v -> load());
                return;
            }
            show(result.data);
        });
    }

    private void show(JsonObject data) {
        bindActivity(data);
        JsonElement overall = data.get("accuracy");
        binding.txtOverall.setText(overall == null || overall.isJsonNull() ? ""
                : getString(R.string.stats_overall, percent(overall.getAsDouble())));
        binding.partBars.removeAllViews();
        for (JsonElement item : rows(data, "by_part")) {
            JsonObject part = item.getAsJsonObject();
            ItemStatsPartBinding row = ItemStatsPartBinding.inflate(getLayoutInflater(), binding.partBars, true);
            long total = number(part, "total");
            row.txtName.setText(getString(R.string.stats_part_name, (int) number(part, "part_id")));
            JsonElement accuracy = part.get("accuracy");
            double value = accuracy == null || accuracy.isJsonNull() ? 0 : accuracy.getAsDouble();
            row.txtValue.setText(total == 0 ? getString(R.string.stats_part_empty)
                    : getString(R.string.stats_part_value, (int) number(part, "correct"), (int) total, percent(value)));
            row.progress.setProgress((int) Math.round(value));
        }
        binding.txtScoreNote.setText(scoreNote(data));

        binding.weakList.removeAllViews();
        for (JsonElement item : rows(data, "weak_types")) {
            JsonObject weak = item.getAsJsonObject();
            ItemWeakTopicBinding row = ItemWeakTopicBinding.inflate(getLayoutInflater(), binding.weakList, true);
            row.txtName.setText(getString(R.string.stats_weak_name, (int) number(weak, "part_id"),
                    QuestionTypeNames.of(value(weak, "question_type"))));
            row.txtValue.setText(getString(R.string.stats_weak_value, percent(weak.get("accuracy").getAsDouble())));
        }
        if (binding.weakList.getChildCount() == 0) {
            TextView hint = new TextView(requireContext(), null, 0, R.style.Text_Caption);
            hint.setText(value(data, "recommendation"));
            binding.weakList.addView(hint);
        }
    }

    private String scoreNote(JsonObject data) {
        String note = value(data, "score_note");
        if (!data.has("reading_prediction")) return note;

        JsonObject prediction = data.getAsJsonObject("reading_prediction");
        JsonElement estimate = prediction.get("estimated_correct");
        if (estimate == null || estimate.isJsonNull()) return getString(R.string.stats_prediction_empty);

        return getString(R.string.stats_prediction_value, estimate.getAsInt(),
                (int) number(prediction, "questions_per_test"), (int) number(prediction, "sample_count"));
    }

    /** Cột hoạt động của 7 ngày gần nhất, tính theo giờ Việt Nam như server. */
    private void bindActivity(JsonObject data) {
        Map<String, Long> counts = new HashMap<>();
        for (JsonElement item : rows(data, "activity")) {
            JsonObject day = item.getAsJsonObject();
            counts.put(value(day, "day"), number(day, "count"));
        }
        TimeZone zone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setTimeZone(zone);
        Calendar day = Calendar.getInstance(zone);
        day.add(Calendar.DAY_OF_YEAR, -6);
        String[] names = getResources().getStringArray(R.array.stats_weekdays);
        long[] values = new long[7];
        String[] labels = new String[7];
        long max = 1;
        long sum = 0;
        for (int i = 0; i < 7; i++) {
            Long count = counts.get(format.format(day.getTime()));
            values[i] = count == null ? 0 : count;
            labels[i] = names[day.get(Calendar.DAY_OF_WEEK) - 1];
            max = Math.max(max, values[i]);
            sum += values[i];
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        binding.barRow.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 7; i++) {
            ItemStatsDayBinding column = ItemStatsDayBinding.inflate(getLayoutInflater(), binding.barRow, true);
            column.txtCount.setText(String.valueOf(values[i]));
            column.txtDay.setText(labels[i]);
            ViewGroup.LayoutParams params = column.bar.getLayoutParams();
            params.height = Math.round(Math.max(4, BAR_MAX_DP * values[i] / (float) max) * density);
            column.bar.setLayoutParams(params);
            column.bar.setBackgroundColor(ContextCompat.getColor(requireContext(),
                    i == 6 ? R.color.app_primary : R.color.app_primary_soft));
        }
        binding.txtActivityTotal.setText(String.valueOf(sum));
        binding.txtStreak.setText(getString(R.string.stats_streak,
                (int) number(data, "current_streak"), (int) number(data, "longest_streak")));
    }

    private static String percent(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public void onDestroyView() {
        if (pending != null) pending.cancel();
        pending = null;
        binding = null;
        super.onDestroyView();
    }
}
