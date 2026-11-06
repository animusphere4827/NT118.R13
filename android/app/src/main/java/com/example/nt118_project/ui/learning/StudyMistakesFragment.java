package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static com.example.nt118_project.learning.StudyJson.number;
import static com.example.nt118_project.learning.StudyJson.rows;
import static com.example.nt118_project.learning.StudyJson.value;

public final class StudyMistakesFragment extends StudyFragment {
    private static final int PAGE_SIZE = 20;
    private int page = 1;

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        super.onViewCreated(view, saved);
        if (saved != null) page = saved.getInt("page", 1);
        title("Ôn câu sai");
        load();
    }

    private void load() {
        body.removeAllViews();
        request(api.mistakes(page, PAGE_SIZE), data -> {
            JsonArray mistakes = rows(data, "rows");
            if (mistakes.size() == 0) {
                text("Chưa có câu sai cần ôn ở trang này.", false);
            } else {
                text("Chọn câu sai hoặc bỏ trống để xem lại đáp án và lời giải.", false);
            }

            for (JsonElement item : mistakes) {
                JsonObject row = item.getAsJsonObject();
                button("Part " + number(row, "part_id") + " · " + value(row, "question_text"),
                    () -> openQuestion(row));
            }

            if (page > 1) button("Trang trước", () -> {
                page--;
                load();
            });
            if ((long) page * PAGE_SIZE < number(data, "total")) button("Trang tiếp", () -> {
                page++;
                load();
            });
            button("Về thống kê", () -> NavHostFragment.findNavController(this).popBackStack());
        }, this::load);
    }

    private void openQuestion(JsonObject row) {
        Bundle args = new Bundle();
        args.putString("kind", value(row, "kind"));
        args.putLong("attemptId", number(row, "attempt_id"));
        args.putLong("questionId", number(row, "question_id"));
        open(R.id.exam_take, args);
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        out.putInt("page", page);
        super.onSaveInstanceState(out);
    }
}
