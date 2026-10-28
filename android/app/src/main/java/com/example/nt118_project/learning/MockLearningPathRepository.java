package com.example.nt118_project.learning;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dữ liệu lộ trình GIẢ LẬP để phát triển giao diện khi chưa có API Course (B2.1).
 * Tên bài khớp bộ nội dung thật trong server/content; tiến độ 3 bài đầu là giả định.
 * Chỉ dùng khi BuildConfig.TV3_MOCK_LEARNING_PATH bật (build debug).
 */
public final class MockLearningPathRepository implements LearningPathRepository {

    private static final long FAKE_LATENCY_MS = 400;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @NonNull
    @Override
    public Cancellable load(@NonNull Callback callback) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Future<?> task = executor.submit(() -> {
            try {
                Thread.sleep(FAKE_LATENCY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            List<PathUnit> units = sampleUnits();
            main.post(() -> {
                if (!cancelled.get()) {
                    callback.onSuccess(units);
                }
            });
        });
        return () -> {
            cancelled.set(true);
            task.cancel(true);
        };
    }

    @Override
    public boolean isMock() {
        return true;
    }

    /** Giải phóng thread nền; gọi khi ViewModel bị hủy. */
    public void shutdown() {
        executor.shutdownNow();
    }

    @NonNull
    public static List<PathUnit> sampleUnits() {
        return Arrays.asList(
                new PathUnit("u1", "Phần 1 · Office", "Văn phòng, nhân sự và từ loại",
                        Arrays.asList(
                                lesson("l1", "Office & Administration", "26 từ vựng", PathLesson.Type.VOCABULARY, 8, true),
                                lesson("l2", "Personnel & Hiring", "26 từ vựng", PathLesson.Type.VOCABULARY, 8, true),
                                lesson("l3", "Word Forms", "Ngữ pháp · từ loại và vị trí", PathLesson.Type.GRAMMAR, 6, true),
                                lesson("l4", "Part 5 · Từ loại và thì động từ", "Luyện 15 câu", PathLesson.Type.PRACTICE_PART5, 10, false))),
                new PathUnit("u2", "Phần 2 · Meetings", "Họp, hội nghị và tiếp thị",
                        Arrays.asList(
                                lesson("l5", "Meetings & Conferences", "26 từ vựng", PathLesson.Type.VOCABULARY, 8, false),
                                lesson("l6", "Marketing & Sales", "26 từ vựng", PathLesson.Type.VOCABULARY, 8, false),
                                lesson("l7", "Verb Tenses & Forms", "Ngữ pháp · thì và bị động", PathLesson.Type.GRAMMAR, 8, false),
                                lesson("l8", "Part 6 · Thư và thông báo", "2 đoạn văn · 8 câu", PathLesson.Type.READING, 10, false))),
                new PathUnit("u3", "Phần 3 · Finance", "Tài chính, đi lại và liên từ",
                        Arrays.asList(
                                lesson("l9", "Finance & Banking", "26 từ vựng", PathLesson.Type.VOCABULARY, 8, false),
                                lesson("l10", "Travel & Transportation", "26 từ vựng", PathLesson.Type.VOCABULARY, 8, false),
                                lesson("l11", "Prepositions & Conjunctions", "Ngữ pháp · giới từ, liên từ", PathLesson.Type.GRAMMAR, 6, false),
                                lesson("l12", "Part 7 · Văn bản ngắn", "3 nhóm văn bản · 9 câu", PathLesson.Type.READING, 15, false))),
                new PathUnit("u4", "Phần 4 · Thi thử", "Đề Reading đầy đủ 100 câu / 75 phút",
                        Arrays.asList(
                                lesson("l13", "Đề luyện Reading số 1", "100 câu · 75 phút", PathLesson.Type.MOCK_TEST, 75, false),
                                lesson("l14", "Đề luyện Reading số 2", "100 câu · 75 phút", PathLesson.Type.MOCK_TEST, 75, false))));
    }

    private static PathLesson lesson(String id, String title, String subtitle, PathLesson.Type type,
                                     int minutes, boolean completed) {
        return new PathLesson(id, title, subtitle, type, minutes, completed);
    }
}
