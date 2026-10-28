package com.example.nt118_project.learning;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.example.nt118_project.R;

/**
 * Chọn màn hình đích trong nav_graph cho từng loại bài. Các màn Quiz/Reading/Thi thử
 * hiện là layout tĩnh; tuần 2 (L2.1–L2.3) sẽ đọc ARG_LESSON_ID để mở đúng nội dung.
 * Ngữ pháp tạm mở màn Part 5 vì chưa có màn bài ngữ pháp riêng trong Figma.
 */
public final class LessonRouter {

    public static final String ARG_LESSON_ID = "lessonId";

    private LessonRouter() {
    }

    @IdRes
    public static int destinationFor(@NonNull PathLesson.Type type) {
        switch (type) {
            case VOCABULARY:
                return R.id.flashcard_front;
            case READING:
                return R.id.reading_list;
            case MOCK_TEST:
                return R.id.exam_list;
            case GRAMMAR:
            case PRACTICE_PART5:
            default:
                return R.id.quiz_part5;
        }
    }
}
