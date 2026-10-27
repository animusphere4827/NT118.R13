package com.example.nt118_project.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.remote.api.AuthApi;
import com.example.nt118_project.data.remote.dto.AuthResponse;
import com.example.nt118_project.data.remote.dto.RefreshRequest;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Khi request có Bearer token bị 401 (access token 15 phút đã hết hạn), gọi POST /auth/refresh rồi gửi lại request.
 * Refresh chạy tuần tự: server thu hồi cả phiên nếu một refresh token được gửi hai lần, nên các request
 * song song chờ nhau và request đến sau dùng luôn token vừa được làm mới.
 * Chỉ kết thúc phiên khi chính refresh bị 401; lỗi mạng hay 5xx thì giữ phiên để lần sau thử lại.
 */
public class TokenAuthenticator implements Authenticator {

    private static final String TAG = "TokenAuthenticator";

    private final SessionManager session;
    private final Object lock = new Object();
    private volatile AuthApi api;

    public TokenAuthenticator(SessionManager session) {
        this.session = session;
    }

    /** Gắn AuthApi sau khi Retrofit được tạo (Retrofit lại cần OkHttpClient chứa authenticator này). */
    void attach(AuthApi api) {
        this.api = api;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) {
        String sent = bearerOf(response.request());
        // Request không gắn token (đăng nhập, refresh, phiên giả) hoặc đã thử lại một lần: trả 401 cho nơi gọi
        if (sent == null || response.priorResponse() != null || api == null) {
            return null;
        }
        synchronized (lock) {
            String current = session.token();
            if (current == null) {
                return null;
            }
            if (!current.equals(sent)) {
                // Request khác vừa refresh xong trong lúc request này chờ khóa
                return withToken(response.request(), current);
            }
            String refreshToken = session.refreshToken();
            if (refreshToken == null) {
                session.expire();
                return null;
            }
            try {
                retrofit2.Response<AuthResponse> r = api.refresh(new RefreshRequest(refreshToken)).execute();
                AuthResponse body = r.body();
                if (r.isSuccessful() && body != null && body.accessToken() != null && body.refreshToken != null) {
                    session.updateTokens(body.accessToken(), body.refreshToken);
                    if (body.user != null) {
                        session.updateUser(body.user);
                    }
                    return withToken(response.request(), body.accessToken());
                }
                if (r.code() == 401) {
                    session.expire();
                }
                return null;
            } catch (IOException e) {
                Log.w(TAG, "Không refresh được token do lỗi mạng", e);
                return null;
            }
        }
    }

    @Nullable
    private static String bearerOf(Request request) {
        String header = request.header("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    private static Request withToken(Request request, String token) {
        return request.newBuilder().header("Authorization", "Bearer " + token).build();
    }
}
