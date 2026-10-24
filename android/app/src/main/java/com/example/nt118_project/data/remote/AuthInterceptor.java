package com.example.nt118_project.data.remote;

import androidx.annotation.NonNull;

import com.example.nt118_project.data.local.SessionManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Gắn "Authorization: Bearer token" vào mọi request khi đã đăng nhập.
 * Nếu server trả 401 cho request có token thì phiên đã hết hạn: xóa phiên và báo cho UI.
 * Request đăng nhập/đăng ký đánh dấu header {@link #NO_AUTH} để không gắn token và không coi 401 là hết phiên.
 */
public class AuthInterceptor implements Interceptor {

    public static final String NO_AUTH = "No-Auth: true";
    private static final String NO_AUTH_HEADER = "No-Auth";

    private final SessionManager session;

    public AuthInterceptor(SessionManager session) {
        this.session = session;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        boolean skip = original.header(NO_AUTH_HEADER) != null;
        Request.Builder builder = original.newBuilder().removeHeader(NO_AUTH_HEADER);
        String token = skip ? null : session.token();
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        Response response = chain.proceed(builder.build());
        if (response.code() == 401 && token != null && token.equals(session.token())) {
            session.expire();
        }
        return response;
    }
}
