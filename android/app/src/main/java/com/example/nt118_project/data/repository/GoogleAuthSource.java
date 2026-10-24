package com.example.nt118_project.data.repository;

import androidx.annotation.Nullable;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.AuthApi;
import com.example.nt118_project.data.remote.dto.AuthResponse;
import com.example.nt118_project.data.remote.dto.GoogleLoginRequest;

/** Đổi Google ID token lấy JWT của hệ thống. Có bản gọi server và bản giả lập. */
public interface GoogleAuthSource {

    void exchange(@Nullable String idToken, ApiCalls.Result<AuthResponse> result);

    /** Gọi POST /auth/google. */
    class Remote implements GoogleAuthSource {
        private final AuthApi api;
        private final ApiCalls calls;

        public Remote(AuthApi api, ApiCalls calls) {
            this.api = api;
            this.calls = calls;
        }

        @Override
        public void exchange(@Nullable String idToken, ApiCalls.Result<AuthResponse> result) {
            if (idToken == null) {
                result.onResult(Resource.error(calls.string(R.string.error_google_failed), 0));
                return;
            }
            calls.enqueue(api.google(new GoogleLoginRequest(idToken)), result);
        }
    }
}
