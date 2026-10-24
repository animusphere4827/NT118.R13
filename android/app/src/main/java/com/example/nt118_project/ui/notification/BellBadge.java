package com.example.nt118_project.ui.notification;

import android.view.View;

import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.badge.ExperimentalBadgeUtils;

/** Gắn badge số thông báo chưa đọc lên nút chuông (id btnBell) ở thanh trên cùng trang chủ. */
public final class BellBadge {

    private BellBadge() {
    }

    @OptIn(markerClass = ExperimentalBadgeUtils.class)
    public static void attach(Fragment fragment, View bell) {
        BadgeDrawable badge = BadgeDrawable.create(fragment.requireContext());
        badge.setBackgroundColor(ContextCompat.getColor(fragment.requireContext(), R.color.app_error));
        badge.setBadgeTextColor(ContextCompat.getColor(fragment.requireContext(), R.color.white));
        badge.setMaxCharacterCount(3);
        badge.setHorizontalOffset(dp(bell, 10));
        badge.setVerticalOffset(dp(bell, 10));
        badge.setVisible(false);
        // Cần đo xong kích thước nút mới gắn được badge đúng vị trí
        bell.post(() -> BadgeUtils.attachBadgeDrawable(badge, bell));

        App.container().notificationRepository.unreadCount().observe(fragment.getViewLifecycleOwner(), n -> {
            int count = n == null ? 0 : n;
            badge.setVisible(count > 0);
            badge.setNumber(count);
            bell.setContentDescription(count > 0
                    ? fragment.getString(R.string.notif_bell_unread, count)
                    : fragment.getString(R.string.title_notifications));
        });
        App.container().notificationRepository.refreshUnread();
    }

    private static int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }
}
