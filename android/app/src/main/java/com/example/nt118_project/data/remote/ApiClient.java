package com.example.nt118_project.data.remote;

import com.example.nt118_project.AppConfig;
import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.remote.api.AuthApi;
import com.google.gson.Gson;

import java.util.concurrent.TimeUnit;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Tạo Retrofit dùng chung với địa chỉ API lấy từ {@link AppConfig}. */
public final class ApiClient {

    private final Retrofit retrofit;

    public ApiClient(AppConfig config, SessionManager session, Gson gson) {
        TokenAuthenticator authenticator = new TokenAuthenticator(session);
        OkHttpClient.Builder http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(new AuthInterceptor(session))
                .authenticator(authenticator);
        if (config.debug) {
            HttpLoggingInterceptor body = new HttpLoggingInterceptor();
            body.setLevel(HttpLoggingInterceptor.Level.BODY);
            // Không in token ra Logcat
            body.redactHeader("Authorization");
            HttpLoggingInterceptor basic = new HttpLoggingInterceptor();
            basic.setLevel(HttpLoggingInterceptor.Level.BASIC);
            http.addInterceptor(chain -> isSensitive(chain.request())
                    ? basic.intercept(chain) : body.intercept(chain));
        }
        retrofit = new Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(http.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        authenticator.attach(retrofit.create(AuthApi.class));
    }

    /**
     * /auth/* chứa mật khẩu, OTP, access/refresh token; multipart là ảnh vài MB.
     * Chỉ in dòng tóm tắt (method, URL, mã, thời gian) cho các request này.
     */
    private static boolean isSensitive(Request request) {
        return request.url().encodedPath().contains("/auth/") || request.body() instanceof MultipartBody;
    }

    /**
     * Retrofit riêng cho dịch vụ bên ngoài (từ điển mở): không gắn token, không tự refresh phiên.
     * Các interface dùng URL tuyệt đối nên baseUrl chỉ để Retrofit hợp lệ.
     */
    public static <T> T external(Class<T> service, AppConfig config, Gson gson) {
        OkHttpClient.Builder http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS);
        if (config.debug) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BASIC);
            http.addInterceptor(log);
        }
        return new Retrofit.Builder()
                .baseUrl("https://api.dictionaryapi.dev/")
                .client(http.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(service);
    }

    public <T> T create(Class<T> service) {
        return retrofit.create(service);
    }

    public Retrofit retrofit() {
        return retrofit;
    }
}
