package com.example.nt118_project.ui.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.nt118_project.ui.profile.ProfileFragment;
import com.example.nt118_project.ui.review.ReviewDecksFragment;
import com.example.nt118_project.ui.review.ReviewEmptyFragment;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;

/**
 * Hiệu ứng chuyển màn 250ms cho các màn của nhóm tài khoản, từ điển, ôn tập, thông báo, huy hiệu.
 * Màn gốc của tab dùng fade through; màn đi sâu (chi tiết, sửa, lật thẻ) trượt ngang shared axis.
 */
public final class ScreenTransitions extends FragmentManager.FragmentLifecycleCallbacks {

    private static final long DURATION_MS = 250;
    private static final String[] PACKAGES = {".ui.auth.", ".ui.profile.", ".ui.dictionary.", ".ui.review.",
            ".ui.notification.", ".ui.badge."};

    /** Gắn vào childFragmentManager của NavHostFragment. */
    public static void install(FragmentManager navHostChildManager) {
        navHostChildManager.registerFragmentLifecycleCallbacks(new ScreenTransitions(), false);
    }

    @Override
    public void onFragmentPreAttached(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull Context context) {
        if (!isOwnScreen(f)) {
            return;
        }
        if (isTabRoot(f)) {
            f.setEnterTransition(fade());
            f.setExitTransition(fade());
            f.setReenterTransition(fade());
            f.setReturnTransition(fade());
        } else {
            f.setEnterTransition(axis(true));
            f.setReturnTransition(axis(false));
            f.setExitTransition(axis(true));
            f.setReenterTransition(axis(false));
        }
    }

    private static boolean isOwnScreen(Fragment f) {
        String name = f.getClass().getName();
        for (String p : PACKAGES) {
            if (name.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTabRoot(Fragment f) {
        return f instanceof ReviewDecksFragment || f instanceof ReviewEmptyFragment || f instanceof ProfileFragment;
    }

    private static MaterialSharedAxis axis(boolean forward) {
        MaterialSharedAxis t = new MaterialSharedAxis(MaterialSharedAxis.X, forward);
        t.setDuration(DURATION_MS);
        return t;
    }

    private static MaterialFadeThrough fade() {
        MaterialFadeThrough t = new MaterialFadeThrough();
        t.setDuration(DURATION_MS);
        return t;
    }
}
