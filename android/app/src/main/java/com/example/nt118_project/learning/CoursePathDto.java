package com.example.nt118_project.learning;

import java.util.List;

public final class CoursePathDto {
    public List<Unit> units;
    public String current_lesson_id;

    public static final class Unit {
        public String id;
        public String title;
        public String description;
        public List<Lesson> lessons;
    }

    public static final class Lesson {
        public String id;
        public String title;
        public String subtitle;
        public PathLesson.Type type;
        public int minutes;
        public boolean completed;
        public long content_id;
        public PathUnlockPolicy.State status;
    }
}
