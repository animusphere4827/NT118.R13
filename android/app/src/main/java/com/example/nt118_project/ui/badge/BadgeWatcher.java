package com.example.nt118_project.ui.badge;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;

import com.example.nt118_project.App;
import com.example.nt118_project.AppContainer;
import com.example.nt118_project.R;
import com.example.nt118_project.common.ServerTime;
import com.example.nt118_project.data.remote.dto.BadgeDto;
import com.example.nt118_project.data.remote.dto.UserDto;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Tự mở màn chúc mừng khi người học vừa đạt huy hiệu mới (M3.2).
 * Server chỉ trao huy hiệu khi gọi GET /learn/badges, nên app gọi API này khi mở lại app,
 * về trang chủ hoặc xem kết quả bài thi, rồi so với danh sách đã chúc mừng lưu trên máy theo từng tài khoản.
 * Chỉ hiện ở các màn "an toàn" để không cắt ngang lúc đang làm bài; mỗi lần hiện một huy hiệu.
 */
public final class BadgeWatcher {

    /** Khoảng cách tối thiểu giữa hai lần kiểm tra không bắt buộc. */
    private static final long MIN_INTERVAL_MS = 15_000;
    /** Lần đầu trên máy này: chỉ chúc mừng huy hiệu mở khóa trong khoảng này, huy hiệu cũ coi như đã xem. */
    private static final long FRESH_MS = 10 * 60_000;

    private static final Set<Integer> SAFE_SCREENS = new HashSet<>(java.util.Arrays.asList(
            R.id.home, R.id.learn_path, R.id.review_decks, R.id.exam_result, R.id.stats,
            R.id.study_history, R.id.profile, R.id.badges, R.id.notifications, R.id.notifications_empty));
    /** Màn vừa có kết quả học: kiểm tra ngay, không chờ khoảng cách tối thiểu. */
    private static final Set<Integer> RESULT_SCREENS = new HashSet<>(java.util.Arrays.asList(
            R.id.home, R.id.exam_result));

    private final SharedPreferences prefs;
    private long lastCheck = -MIN_INTERVAL_MS;
    private boolean checking;

    public BadgeWatcher(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("badge_seen", Context.MODE_PRIVATE);
    }

    public static boolean isSafe(@Nullable NavDestination destination) {
        return destination != null && SAFE_SCREENS.contains(destination.getId());
    }

    public void onDestination(NavController nav, NavDestination destination) {
        if (isSafe(destination)) check(nav, RESULT_SCREENS.contains(destination.getId()));
    }

    public void check(NavController nav, boolean force) {
        AppContainer c = App.container();
        if (!c.session.isLoggedIn() || c.session.isFakeSession()) return;
        UserDto user = c.session.currentUser().getValue();
        if (user == null || checking) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastCheck < MIN_INTERVAL_MS) return;
        checking = true;
        lastCheck = now;
        String key = "seen_" + user.id;
        c.badgeRepository.badges(r -> {
            checking = false;
            if (!r.isSuccess() || r.data == null || r.data.rows == null) return;
            UserDto current = c.session.currentUser().getValue();
            if (current == null || current.id != user.id) return;
            boolean firstRun = !prefs.contains(key);
            Set<String> seen = new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
            BadgeDto fresh = null;
            for (BadgeDto badge : r.data.rows) {
                if (!badge.unlocked || badge.code == null || seen.contains(badge.code)) continue;
                boolean candidate = !firstRun || isRecent(badge.unlockedAt);
                if (!candidate) {
                    seen.add(badge.code);
                } else if (fresh == null) {
                    fresh = badge;
                }
                // Các huy hiệu mới khác giữ chưa xem để lần kiểm tra sau chúc mừng tiếp
            }
            boolean canShow = fresh != null && isSafe(nav.getCurrentDestination());
            if (canShow) seen.add(fresh.code);
            prefs.edit().putStringSet(key, seen).apply();
            if (canShow) {
                nav.navigate(R.id.badge_congrats, BadgeCongratsFragment.args(fresh));
                // Còn huy hiệu khác thì kiểm tra lại khi người học quay về màn an toàn
                lastCheck = -MIN_INTERVAL_MS;
            }
        });
    }

    private static boolean isRecent(@Nullable String unlockedAt) {
        Date at = ServerTime.parse(unlockedAt);
        return at != null && System.currentTimeMillis() - at.getTime() <= FRESH_MS;
    }
}
