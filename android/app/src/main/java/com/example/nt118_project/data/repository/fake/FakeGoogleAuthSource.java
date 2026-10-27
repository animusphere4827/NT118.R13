package com.example.nt118_project.data.repository.fake;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.dto.AuthResponse;
import com.example.nt118_project.data.remote.dto.UserDto;
import com.example.nt118_project.data.repository.GoogleAuthSource;

/**
 * Giả lập POST /auth/firebase khi chưa có google-services.json hoặc Firebase chưa bật Google provider.
 * Trả token giả (tiền tố {@link SessionManager#FAKE_TOKEN_PREFIX}) nên các API thật khác sẽ không nhận phiên này.
 */
public class FakeGoogleAuthSource implements GoogleAuthSource {

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void exchange(@Nullable String idToken, ApiCalls.Result<AuthResponse> result) {
        AuthResponse response = new AuthResponse();
        response.token = SessionManager.FAKE_TOKEN_PREFIX + "google";
        response.isNewUser = false;
        UserDto user = new UserDto();
        user.id = 9001;
        user.email = "minhanh.google@gmail.com";
        user.fullName = "Nguyễn Minh Anh";
        user.avatarUrl = null;
        user.role = "STUDENT";
        user.status = "ACTIVE";
        user.targetScore = 450;
        response.user = user;
        // Mô phỏng độ trễ mạng để thấy trạng thái đang tải
        main.postDelayed(() -> result.onResult(Resource.success(response)), 600);
    }
}
