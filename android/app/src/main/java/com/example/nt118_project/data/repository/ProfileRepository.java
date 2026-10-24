package com.example.nt118_project.data.repository;

import androidx.annotation.Nullable;

import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.AccountApi;
import com.example.nt118_project.data.remote.dto.UpdateProfileRequest;
import com.example.nt118_project.data.remote.dto.UserDto;

/** Cập nhật hồ sơ (họ tên, mục tiêu Reading, avatar URL); lưu lại người dùng mới vào phiên. */
public interface ProfileRepository {

    void update(String fullName, int targetScore, @Nullable String avatarUrl, ApiCalls.Result<UserDto> result);

    class Remote implements ProfileRepository {
        private final AccountApi api;
        private final ApiCalls calls;
        private final SessionManager session;

        public Remote(AccountApi api, ApiCalls calls, SessionManager session) {
            this.api = api;
            this.calls = calls;
            this.session = session;
        }

        @Override
        public void update(String fullName, int targetScore, @Nullable String avatarUrl, ApiCalls.Result<UserDto> result) {
            calls.enqueue(api.updateProfile(new UpdateProfileRequest(fullName, targetScore, avatarUrl)), r -> {
                if (r.isSuccess() && r.data != null) {
                    session.updateUser(r.data);
                }
                result.onResult(r);
            });
        }
    }
}
