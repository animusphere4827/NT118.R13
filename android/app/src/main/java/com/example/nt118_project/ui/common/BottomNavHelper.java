package com.example.nt118_project.ui.common;

import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;

import com.example.nt118_project.R;

/** Nối thanh điều hướng dưới (layout_bottom_nav) với NavController. */
public final class BottomNavHelper {

    private BottomNavHelper() {
    }

    /** Gắn click cho 5 tab; selectedTab là id tab đang hiển thị (R.id.tabHome...). */
    public static void bind(View root, NavController nav, @IdRes int selectedTab) {
        link(root, nav, R.id.tabHome, R.id.home, selectedTab);
        link(root, nav, R.id.tabLearn, R.id.learn_path, selectedTab);
        link(root, nav, R.id.tabReview, R.id.review_decks, selectedTab);
        link(root, nav, R.id.tabExam, R.id.exam_list, selectedTab);
        link(root, nav, R.id.tabProfile, R.id.profile, selectedTab);
    }

    private static void link(View root, NavController nav, @IdRes int tabId,
                             @IdRes int destId, @IdRes int selectedTab) {
        LinearLayout tab = root.findViewById(tabId);
        boolean selected = tabId == selectedTab;
        int color = ContextCompat.getColor(root.getContext(),
                selected ? R.color.app_primary : R.color.app_text_secondary);
        ((ImageView) tab.getChildAt(0)).setColorFilter(color);
        TextView label = (TextView) tab.getChildAt(1);
        label.setTextColor(color);
        label.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setSelected(selected);
        tab.setOnClickListener(v -> {
            if (selected) {
                return;
            }
            // Các tab ngang hàng: giữ trang chủ ở đáy back stack, Back từ tab quay về trang chủ
            NavOptions options = new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.home, destId == R.id.home)
                    .build();
            nav.navigate(destId, null, options);
        });
    }
}
