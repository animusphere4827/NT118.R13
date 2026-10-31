package com.example.nt118_project.learning;

import androidx.annotation.NonNull;

public final class PathLesson {

    public enum Type { VOCABULARY, GRAMMAR, PRACTICE_PART5, READING, MOCK_TEST }

    public final String id;
    public final String title;
    public final String subtitle;
    public final Type type;
    public final int minutes;
    public final boolean completed;
    public final long contentId;
    public final PathUnlockPolicy.State serverState;
    public final boolean recommended;

    public PathLesson(@NonNull String id, @NonNull String title, @NonNull String subtitle,
                      @NonNull Type type, int minutes, boolean completed) {
        this(id, title, subtitle, type, minutes, completed, 0, null, false);
    }

    public PathLesson(@NonNull String id, @NonNull String title, @NonNull String subtitle,
                      @NonNull Type type, int minutes, boolean completed, long contentId,
                      PathUnlockPolicy.State serverState, boolean recommended) {
        this.contentId = contentId;
        this.serverState = serverState;
        this.recommended = recommended;
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.type = type;
        this.minutes = minutes;
        this.completed = completed;
    }
}
