package com.example.nt118_project.data.repository;

import android.util.Log;

import androidx.annotation.Nullable;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.AuthApi;
import com.example.nt118_project.data.remote.dto.AuthResponse;
import com.example.nt118_project.data.remote.dto.GoogleLoginRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/** Đổi Google ID token lấy JWT của hệ thống. Có bản gọi server và bản giả lập. */
public interface GoogleAuthSource {

    void exchange(@Nullable String idToken, ApiCalls.Result<AuthResponse> result);

    /** Gọi khi đăng xuất. */
    default void signOut() {
    }

    /**
     * Đăng nhập Firebase bằng Google ID token từ Credential Manager, lấy Firebase ID token
     * rồi gọi POST /auth/firebase. Cần android/app/google-services.json để Firebase khởi tạo.
     */
    class Remote implements GoogleAuthSource {
        private static final String TAG = "GoogleAuthSource";

        private final AuthApi api;
        private final ApiCalls calls;

        public Remote(AuthApi api, ApiCalls calls) {
            this.api = api;
            this.calls = calls;
        }

        @Override
        public void exchange(@Nullable String idToken, ApiCalls.Result<AuthResponse> result) {
            FirebaseAuth auth = firebase();
            if (idToken == null || auth == null) {
                fail(result);
                return;
            }
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                    .addOnSuccessListener(signIn -> {
                        FirebaseUser user = signIn.getUser();
                        if (user == null) {
                            fail(result);
                            return;
                        }
                        user.getIdToken(false)
                                .addOnSuccessListener(token -> {
                                    String firebaseToken = token.getToken();
                                    if (firebaseToken == null) {
                                        fail(result);
                                    } else {
                                        calls.enqueue(api.firebase(new GoogleLoginRequest(firebaseToken)), result);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "Không lấy được Firebase ID token", e);
                                    fail(result);
                                });
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Firebase từ chối Google credential", e);
                        fail(result);
                    });
        }

        @Override
        public void signOut() {
            FirebaseAuth auth = firebase();
            if (auth != null) {
                auth.signOut();
            }
        }

        private void fail(ApiCalls.Result<AuthResponse> result) {
            result.onResult(Resource.error(calls.string(R.string.error_google_failed), 0));
        }

        /** null khi Firebase chưa khởi tạo (thiếu google-services.json). */
        @Nullable
        private static FirebaseAuth firebase() {
            try {
                return FirebaseAuth.getInstance();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Firebase chưa được cấu hình, kiểm tra google-services.json", e);
                return null;
            }
        }
    }
}
