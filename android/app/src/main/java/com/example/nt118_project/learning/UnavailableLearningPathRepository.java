package com.example.nt118_project.learning;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

/**
 * Dùng khi tắt mock mà API Course (B2.1) chưa có: báo lỗi rõ ràng,
 * không âm thầm quay về dữ liệu giả lập.
 */
public final class UnavailableLearningPathRepository implements LearningPathRepository {

    static final String MESSAGE = "Chưa có API lộ trình (chờ B2.1 của TV1).";

    private final Handler main = new Handler(Looper.getMainLooper());

    @NonNull
    @Override
    public Cancellable load(@NonNull Callback callback) {
        Runnable deliver = () -> callback.onError(MESSAGE);
        main.post(deliver);
        return () -> main.removeCallbacks(deliver);
    }

    @Override
    public boolean isMock() {
        return false;
    }
}
