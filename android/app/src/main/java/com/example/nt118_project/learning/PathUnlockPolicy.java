package com.example.nt118_project.learning;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quy tắc mở khóa TẠM THỜI của lộ trình (chờ TV1 chốt API Course B2.1):
 * học tuần tự theo thứ tự các phần rồi các bài; bài đã xong là DONE, bài chưa xong đầu tiên
 * là IN_PROGRESS, mọi bài sau đó LOCKED. Khi API trả trạng thái riêng, thay lớp này
 * bằng mapping từ API, giao diện không phải đổi.
 */
public final class PathUnlockPolicy {

    public enum State { DONE, IN_PROGRESS, LOCKED }

    /** Bài kèm trạng thái đã tính, theo đúng thứ tự trong lộ trình. */
    public static final class Entry {
        public final PathUnit unit;
        public final PathLesson lesson;
        public final State state;

        Entry(PathUnit unit, PathLesson lesson, State state) {
            this.unit = unit;
            this.lesson = lesson;
            this.state = state;
        }
    }

    /** Kết quả tính cho toàn bộ lộ trình. */
    public static final class Result {
        public final List<Entry> entries;
        public final int completedCount;
        @Nullable
        public final Entry current;

        Result(List<Entry> entries, int completedCount, @Nullable Entry current) {
            this.entries = Collections.unmodifiableList(entries);
            this.completedCount = completedCount;
            this.current = current;
        }

        public int totalCount() {
            return entries.size();
        }

        /** Tiến độ 0–100, làm tròn xuống; lộ trình rỗng là 0. */
        public int percent() {
            return entries.isEmpty() ? 0 : completedCount * 100 / entries.size();
        }

        public boolean isFinished() {
            return !entries.isEmpty() && current == null;
        }

        @NonNull
        public List<Entry> entriesOf(@NonNull PathUnit unit) {
            List<Entry> out = new ArrayList<>();
            for (Entry e : entries) {
                if (e.unit == unit) {
                    out.add(e);
                }
            }
            return out;
        }
    }

    private PathUnlockPolicy() {
    }

    /**
     * Một bài đã đánh dấu xong nhưng nằm sau bài chưa xong (dữ liệu cũ, admin chèn bài mới)
     * vẫn giữ DONE: không khóa lại nội dung người học đã hoàn thành.
     */
    @NonNull
    public static Result evaluate(@NonNull List<PathUnit> units) {
        List<Entry> entries = new ArrayList<>();
        Entry current = null;
        int completed = 0;
        for (PathUnit unit : units) {
            for (PathLesson lesson : unit.lessons) {
                State state;
                if (lesson.completed) {
                    state = State.DONE;
                    completed++;
                } else if (current == null) {
                    state = State.IN_PROGRESS;
                } else {
                    state = State.LOCKED;
                }
                Entry entry = new Entry(unit, lesson, state);
                if (state == State.IN_PROGRESS) {
                    current = entry;
                }
                entries.add(entry);
            }
        }
        return new Result(entries, completed, current);
    }

    /** Chỉ bài DONE (ôn lại) và IN_PROGRESS được mở. */
    public static boolean canOpen(@NonNull State state) {
        return state != State.LOCKED;
    }
}
