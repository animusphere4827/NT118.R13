package com.example.nt118_project.ui.common;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.example.nt118_project.R;
import com.example.nt118_project.databinding.ViewStateBinding;

/** Điều khiển layout view_state: skeleton nhấp nháy khi tải, trạng thái trống hoặc lỗi có nút hành động. */
public final class StateView {

    private final ViewStateBinding b;
    @Nullable
    private ObjectAnimator pulse;

    public StateView(ViewStateBinding binding) {
        this.b = binding;
    }

    /** Skeleton thay cho vòng xoay: các khối xám nhấp nháy nhẹ. */
    public void loading() {
        b.getRoot().setVisibility(View.VISIBLE);
        b.skeleton.setVisibility(View.VISIBLE);
        b.empty.setVisibility(View.GONE);
        b.getRoot().setContentDescription(b.getRoot().getContext().getString(R.string.loading));
        if (pulse == null) {
            pulse = ObjectAnimator.ofFloat(b.skeleton, View.ALPHA, 1f, 0.45f);
            pulse.setDuration(800);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
        }
        if (!pulse.isStarted()) {
            pulse.start();
        }
    }

    public void empty(@DrawableRes int icon, String title, @Nullable String description,
                      @Nullable String action, @Nullable Runnable onAction) {
        stopPulse();
        b.getRoot().setVisibility(View.VISIBLE);
        b.getRoot().setContentDescription(null);
        b.skeleton.setVisibility(View.GONE);
        b.empty.setVisibility(View.VISIBLE);
        b.imgEmpty.setImageResource(icon);
        b.txtEmptyTitle.setText(title);
        b.txtEmptyDesc.setText(description);
        b.txtEmptyDesc.setVisibility(description == null || description.isEmpty() ? View.GONE : View.VISIBLE);
        boolean hasAction = action != null && onAction != null;
        b.btnEmptyAction.setVisibility(hasAction ? View.VISIBLE : View.GONE);
        b.btnEmptyAction.setText(action);
        b.btnEmptyAction.setOnClickListener(hasAction ? v -> onAction.run() : null);
    }

    /** Lỗi tải dữ liệu (thường là mất mạng) kèm nút Thử lại. */
    public void error(@Nullable String message, Runnable retry) {
        android.content.Context c = b.getRoot().getContext();
        empty(R.drawable.ic_wifi_off, c.getString(R.string.state_error_title), message,
                c.getString(R.string.state_retry), retry);
    }

    public void hide() {
        stopPulse();
        b.getRoot().setVisibility(View.GONE);
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            b.skeleton.setAlpha(1f);
        }
    }
}
