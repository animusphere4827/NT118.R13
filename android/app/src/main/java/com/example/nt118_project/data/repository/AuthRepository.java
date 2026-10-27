package com.example.nt118_project.data.repository;

import androidx.annotation.Nullable;

import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.AuthApi;
import com.example.nt118_project.data.remote.dto.AuthResponse;
import com.example.nt118_project.data.remote.dto.LoginRequest;
import com.example.nt118_project.data.remote.dto.RegisterRequest;
import com.example.nt118_project.data.remote.dto.UserDto;

/** Đăng ký, đăng nhập email/Google, kiểm tra phiên và đăng xuất. Lưu phiên vào {@link SessionManager}. */
public class AuthRepository {

    private final AuthApi api;
    private final ApiCalls calls;
    private final SessionManager session;
    private final GoogleAuthSource google;

    public AuthRepository(AuthApi api, ApiCalls calls, SessionManager session, GoogleAuthSource google) {
        this.api = api;
        this.calls = calls;
        this.session = session;
        this.google = google;
    }

    public void login(String email, String password, ApiCalls.Result<AuthResponse> result) {
        calls.enqueue(api.login(new LoginRequest(email, password)), r -> result.onResult(saveSession(r)));
    }

    /** Server chỉ trả {id} khi đăng ký nên đăng nhập ngay sau đó để lấy token. */
    public void register(String fullName, String email, String password, ApiCalls.Result<AuthResponse> result) {
        calls.enqueue(api.register(new RegisterRequest(email, password, fullName)), r -> {
            if (r.isSuccess()) {
                login(email, password, result);
            } else {
                result.onResult(Resource.error(r.message, r.code));
            }
        });
    }

    public void loginWithGoogle(@Nullable String idToken, ApiCalls.Result<AuthResponse> result) {
        google.exchange(idToken, r -> result.onResult(saveSession(r)));
    }

    /**
     * Kiểm tra phiên khi mở app. Access token hết hạn thì TokenAuthenticator tự refresh; refresh bị từ chối
     * thì phiên bị xóa và về đăng nhập. Lỗi mạng thì giữ phiên và dùng thông tin người dùng đã lưu.
     */
    public void refreshMe(ApiCalls.Result<UserDto> result) {
        if (session.isFakeSession()) {
            result.onResult(Resource.success(session.currentUser().getValue()));
            return;
        }
        calls.enqueue(api.me(), r -> {
            if (r.isSuccess()) {
                session.updateUser(r.data);
            }
            result.onResult(r);
        });
    }

    /** Gọi POST /auth/logout (bỏ qua lỗi) rồi xóa phiên local. */
    public void logout(Runnable done) {
        if (session.isFakeSession()) {
            session.clear();
            done.run();
            return;
        }
        calls.enqueue(api.logout(), r -> {
            session.clear();
            done.run();
        });
    }

    private Resource<AuthResponse> saveSession(Resource<AuthResponse> r) {
        if (r.isSuccess() && r.data != null && r.data.accessToken() != null) {
            session.start(r.data.accessToken(), r.data.refreshToken, r.data.user);
        }
        return r;
    }
}
