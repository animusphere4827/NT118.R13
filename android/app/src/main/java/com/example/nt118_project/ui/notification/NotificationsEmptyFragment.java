package com.example.nt118_project.ui.notification;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.nt118_project.R;
import com.example.nt118_project.ui.ScreenFragment;

/** Chưa có thông báo. "Về Trang chủ" nối qua tag; nút cài đặt giờ nhắc học chưa có API nên chỉ báo. */
public class NotificationsEmptyFragment extends ScreenFragment {

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.btnSettings).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.notif_settings_soon, Toast.LENGTH_SHORT).show());
    }
}
