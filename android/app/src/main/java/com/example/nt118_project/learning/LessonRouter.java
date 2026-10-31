package com.example.nt118_project.learning;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.example.nt118_project.R;

public final class LessonRouter {

    public static final String ARG_LESSON_ID = "lessonId";

    private LessonRouter() {
    }

    @IdRes
    public static int destinationFor(@NonNull PathLesson.Type type) {
        switch (type) {
            case VOCABULARY:
                return R.id.vocabulary_lesson;
            case READING:
                return R.id.reading_article;
            case MOCK_TEST:
                return R.id.exam_take;
            case GRAMMAR:
                return R.id.grammar_lesson;
            case PRACTICE_PART5:
            default:
                return R.id.exam_take;
        }
    }
}
