package com.example.nt118_project.ui.review;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.example.nt118_project.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Hộp thoại nhập tên bộ thẻ và xác nhận xóa. */
public final class DeckDialogs {

    public interface OnName {
        void onName(String name);
    }

    private DeckDialogs() {
    }

    public static void askName(Context context, @StringRes int title, @Nullable String initial, OnName onName) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(R.string.deck_name_hint);
        input.setSingleLine(true);
        if (initial != null) {
            input.setText(initial);
            input.setSelection(initial.length());
        }
        FrameLayout box = new FrameLayout(context);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20,
                context.getResources().getDisplayMetrics());
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty() || name.length() > 150) {
                input.setError(context.getString(R.string.error_deck_name));
                return;
            }
            dialog.dismiss();
            onName.onName(name);
        }));
        dialog.show();
    }

    public static void confirm(Context context, @StringRes int title, String message, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.action_delete, (d, w) -> onConfirm.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
