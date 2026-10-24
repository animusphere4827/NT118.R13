package com.example.nt118_project.data.local;

import android.content.Context;
import android.content.SharedPreferences;

/** Cài đặt không nhạy cảm của app (không mã hóa). */
public class AppPrefs {

    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
    }

    /** Đã xem xong màn giới thiệu: lần sau mở app vào thẳng đăng nhập. */
    public boolean isOnboardingDone() {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public void setOnboardingDone() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
    }
}
