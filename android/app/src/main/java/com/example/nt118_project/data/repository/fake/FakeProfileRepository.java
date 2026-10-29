package com.example.nt118_project.data.repository.fake;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.dto.UserDto;
import com.example.nt118_project.data.repository.ProfileRepository;

/** Giả lập PUT /auth/me: chỉ cập nhật người dùng lưu trong máy. Tải ảnh lên cần server thật. */
public class FakeProfileRepository implements ProfileRepository {

    private final SessionManager session;
    private final ApiCalls calls;
    private final Handler main = new Handler(Looper.getMainLooper());

    public FakeProfileRepository(SessionManager session, ApiCalls calls) {
        this.session = session;
        this.calls = calls;
    }

    @Override
    public void update(String fullName, int targetScore, @Nullable String avatarUrl, ApiCalls.Result<UserDto> result) {
        UserDto current = session.currentUser().getValue();
        UserDto user = new UserDto();
        if (current != null) {
            user.id = current.id;
            user.email = current.email;
            user.role = current.role;
            user.status = current.status;
        }
        user.fullName = fullName;
        user.targetScore = targetScore;
        user.avatarUrl = avatarUrl;
        main.postDelayed(() -> {
            session.updateUser(user);
            result.onResult(Resource.success(user));
        }, 600);
    }

    @Override
    public void uploadAvatar(byte[] image, String mimeType, ApiCalls.Result<UserDto> result) {
        main.postDelayed(() -> result.onResult(Resource.error(calls.string(R.string.error_avatar_upload_fake), 0)), 600);
    }
}
