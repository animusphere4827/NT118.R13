package com.example.nt118_project.learning;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/** Một phần (chương) của lộ trình, gồm các bài theo thứ tự học. */
public final class PathUnit {

    public final String id;
    public final String title;
    public final String description;
    public final List<PathLesson> lessons;

    public PathUnit(@NonNull String id, @NonNull String title, @NonNull String description,
                    @NonNull List<PathLesson> lessons) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.lessons = Collections.unmodifiableList(lessons);
    }
}
