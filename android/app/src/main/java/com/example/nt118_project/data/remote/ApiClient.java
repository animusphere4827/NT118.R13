package com.example.nt118_project.data.remote;

import com.example.nt118_project.AppConfig;
import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.remote.api.AuthApi;
import com.google.gson.Gson;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
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
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BODY);
            // Không in token ra Logcat
            log.redactHeader("Authorization");
            http.addInterceptor(log);
        }
        retrofit = new Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(http.build())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        authenticator.attach(retrofit.create(AuthApi.class));
    }

    public <T> T create(Class<T> service) {
        return retrofit.create(service);
    }

    public Retrofit retrofit() {
        return retrofit;
    }
}
