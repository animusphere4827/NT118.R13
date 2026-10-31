package com.example.nt118_project.learning;

import androidx.annotation.NonNull;

import com.example.nt118_project.data.remote.ApiCalls;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;

public final class ApiLearningPathRepository implements LearningPathRepository {
    private final CourseApi api;
    private final ApiCalls calls;

    public ApiLearningPathRepository(CourseApi api, ApiCalls calls) {
        this.api = api;
        this.calls = calls;
    }

    @NonNull
    @Override
    public Cancellable load(@NonNull Callback callback) {
        Call<CoursePathDto> call = api.path();
        calls.enqueue(call, result -> {
            if (call.isCanceled()) return;
            if (!result.isSuccess() || result.data == null || result.data.units == null) {
                callback.onError(result.message == null ? "Không tải được lộ trình" : result.message);
                return;
            }
            List<PathUnit> units = new ArrayList<>();
            for (CoursePathDto.Unit unit : result.data.units) {
                if (unit.lessons == null) {
                    callback.onError("Dữ liệu lộ trình không hợp lệ");
                    return;
                }
                List<PathLesson> lessons = new ArrayList<>();
                for (CoursePathDto.Lesson lesson : unit.lessons) {
                    if (lesson.id == null || lesson.type == null || lesson.status == null || lesson.content_id <= 0) {
                        callback.onError("Dữ liệu bài học không hợp lệ");
                        return;
                    }
                    lessons.add(new PathLesson(lesson.id, lesson.title, lesson.subtitle, lesson.type,
                            lesson.minutes, lesson.completed, lesson.content_id, lesson.status,
                            Objects.equals(lesson.id, result.data.current_lesson_id)));
                }
                units.add(new PathUnit(unit.id, unit.title, unit.description, lessons));
            }
            callback.onSuccess(units);
        });
        return call::cancel;
    }

    @Override
    public boolean isMock() {
        return false;
    }
}
