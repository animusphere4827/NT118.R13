package com.example.nt118_project;

import android.content.Context;

import com.example.nt118_project.data.local.AppPrefs;
import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.local.TokenStore;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.ApiClient;
import com.example.nt118_project.data.remote.api.AuthApi;
import com.example.nt118_project.data.repository.AuthRepository;
import com.example.nt118_project.data.repository.GoogleAuthSource;
import com.example.nt118_project.data.repository.fake.FakeGoogleAuthSource;
import com.google.gson.Gson;

/**
 * Nơi tạo và giữ các đối tượng dùng chung (thay cho thư viện DI).
 * ViewModel lấy phụ thuộc qua {@code App.container()}.
 */
public class AppContainer {

    public final AppConfig config;
    public final Gson gson = new Gson();
    public final SessionManager session;
    public final ApiClient apiClient;
    public final ApiCalls apiCalls;
    public final AuthApi authApi;
    public final AppPrefs appPrefs;
    public final AuthRepository authRepository;

    public AppContainer(Context context) {
        config = AppConfig.load(context);
        session = new SessionManager(new TokenStore(context), gson);
        apiClient = new ApiClient(config, session, gson);
        apiCalls = new ApiCalls(context, gson);
        authApi = apiClient.create(AuthApi.class);
        appPrefs = new AppPrefs(context);
        GoogleAuthSource google = config.fakeGoogleAuth
                ? new FakeGoogleAuthSource()
                : new GoogleAuthSource.Remote(authApi, apiCalls);
        authRepository = new AuthRepository(authApi, apiCalls, session, google);
    }
}
