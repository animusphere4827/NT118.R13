package com.example.nt118_project.learning;

import androidx.annotation.NonNull;

/** Một bài trong lộ trình. Trạng thái hiển thị không lưu ở đây mà do PathUnlockPolicy tính. */
public final class PathLesson {

    /** Loại bài quyết định màn hình được mở khi chạm vào bài. */
    public enum Type { VOCABULARY, GRAMMAR, PRACTICE_PART5, READING, MOCK_TEST }

    public final String id;
    public final String title;
    public final String subtitle;
    public final Type type;
    public final int minutes;
    public final boolean completed;

    public PathLesson(@NonNull String id, @NonNull String title, @NonNull String subtitle,
                      @NonNull Type type, int minutes, boolean completed) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.type = type;
        this.minutes = minutes;
        this.completed = completed;
    }
}
