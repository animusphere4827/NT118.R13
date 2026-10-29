package com.example.nt118_project.data.repository;

import androidx.annotation.Nullable;

import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.AccountApi;
import com.example.nt118_project.data.remote.dto.UpdateProfileRequest;
import com.example.nt118_project.data.remote.dto.UserDto;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/** Cập nhật hồ sơ (họ tên, mục tiêu Reading, avatar URL hoặc ảnh tải lên); lưu lại người dùng mới vào phiên. */
public interface ProfileRepository {

    void update(String fullName, int targetScore, @Nullable String avatarUrl, ApiCalls.Result<UserDto> result);

    /** Tải ảnh lên làm avatar ngay (không chờ bấm Lưu); server trả người dùng đã có avatar_url mới. */
    void uploadAvatar(byte[] image, String mimeType, ApiCalls.Result<UserDto> result);

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

        @Override
        public void uploadAvatar(byte[] image, String mimeType, ApiCalls.Result<UserDto> result) {
            String name = "avatar." + mimeType.substring(mimeType.indexOf('/') + 1);
            MultipartBody.Part part = MultipartBody.Part.createFormData("avatar", name,
                    RequestBody.create(image, MediaType.parse(mimeType)));
            calls.enqueue(api.uploadAvatar(part), r -> {
                if (r.isSuccess() && r.data != null && r.data.user != null) {
                    session.updateUser(r.data.user);
                    result.onResult(Resource.success(r.data.user));
                } else {
                    result.onResult(Resource.error(r.message, r.code));
                }
            });
        }
    }
}
