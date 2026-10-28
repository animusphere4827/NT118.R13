package com.example.nt118_project.ui.learning;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.example.nt118_project.R;
import com.example.nt118_project.databinding.LayoutTv3StateBinding;
import com.example.nt118_project.learning.LearningPathViewModel;

/** Hiển thị đúng một trong bốn trạng thái: tải, lỗi, rỗng hoặc nội dung. */
final class StateViews {

    private StateViews() {
    }

    /**
     * Trả về true khi màn hình nên vẽ nội dung. Đang tải lại mà đã có dữ liệu cũ
     * thì giữ nội dung cũ thay vì nháy màn hình tải.
     */
    static boolean render(@NonNull LearningPathViewModel.UiState ui, @NonNull View content,
                          @NonNull LayoutTv3StateBinding state, @NonNull View mockBadge,
                          @NonNull Runnable retry) {
        mockBadge.setVisibility(ui.mock ? View.VISIBLE : View.GONE);
        boolean hasData = ui.progress != null && !ui.isEmpty();
        boolean showLoading = ui.loading && ui.progress == null;
        state.stateLoading.setVisibility(showLoading ? View.VISIBLE : View.GONE);
        if (ui.error != null) {
            message(state, R.drawable.ic_wifi_off, ui.error, retry);
        } else if (!ui.loading && ui.isEmpty()) {
            message(state, R.drawable.ic_book_open, state.getRoot().getContext().getString(R.string.tv3_empty), retry);
        } else {
            state.stateMessage.setVisibility(View.GONE);
        }
        content.setVisibility(hasData ? View.VISIBLE : View.GONE);
        return hasData;
    }

    private static void message(LayoutTv3StateBinding state, @DrawableRes int icon, String text, Runnable retry) {
        state.stateMessage.setVisibility(View.VISIBLE);
        state.stateIcon.setImageResource(icon);
        state.stateText.setText(text);
        state.stateRetry.setOnClickListener(v -> retry.run());
    }
}
