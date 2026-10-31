package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.learning.LessonRouter;
import com.example.nt118_project.learning.PathUnlockPolicy;

/** Mở bài từ Trang chủ hoặc Lộ trình; chặn bài đang khóa và chạm trùng khi đang chuyển màn. */
final class LessonNavigator {

    private LessonNavigator() {
    }

    static void open(@NonNull Fragment from, @NonNull PathUnlockPolicy.Entry entry) {
        if (!PathUnlockPolicy.canOpen(entry.state)) {
            Toast.makeText(from.requireContext(), R.string.tv3_locked_toast, Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle args = new Bundle();
        args.putString(LessonRouter.ARG_LESSON_ID, entry.lesson.id);
        args.putLong("contentId", entry.lesson.contentId);
        args.putString("kind", entry.lesson.type == com.example.nt118_project.learning.PathLesson.Type.PRACTICE_PART5 ? "practice" : "mock");
        navigate(from, LessonRouter.destinationFor(entry.lesson.type), args);
    }

    /** Chỉ điều hướng khi màn hiện tại vẫn là màn gọi, tránh mở hai lần do chạm nhanh. */
    static void navigate(@NonNull Fragment from, int destination, Bundle args) {
        NavController nav = NavHostFragment.findNavController(from);
        if (nav.getCurrentDestination() == null || !from.isResumed()) {
            return;
        }
        int self = nav.getCurrentDestination().getId();
        if (self != R.id.home && self != R.id.learn_path) {
            return;
        }
        nav.navigate(destination, args);
    }
}
