package com.example.nt118_project;

import android.content.Context;

import com.example.nt118_project.data.local.AppPrefs;
import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.local.TokenStore;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.ApiClient;
import com.example.nt118_project.data.remote.api.AccountApi;
import com.example.nt118_project.data.remote.api.AuthApi;
import com.example.nt118_project.data.remote.api.BadgeApi;
import com.example.nt118_project.data.remote.api.ExternalDictionaryApi;
import com.example.nt118_project.data.remote.api.LearnApi;
import com.example.nt118_project.data.remote.api.NotificationApi;
import com.example.nt118_project.data.repository.AuthRepository;
import com.example.nt118_project.data.repository.BadgeRepository;
import com.example.nt118_project.data.repository.DeckRepository;
import com.example.nt118_project.data.repository.DictionaryRepository;
import com.example.nt118_project.data.repository.ExternalDictionaryRepository;
import com.example.nt118_project.data.repository.GoogleAuthSource;
import com.example.nt118_project.data.repository.NotificationRepository;
import com.example.nt118_project.data.repository.PasswordRepository;
import com.example.nt118_project.data.repository.ProfileRepository;
import com.example.nt118_project.data.repository.fake.FakeBadgeRepository;
import com.example.nt118_project.data.repository.fake.FakeGoogleAuthSource;
import com.example.nt118_project.data.repository.fake.FakePasswordRepository;
import com.example.nt118_project.data.repository.fake.FakeProfileRepository;
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
    public final AccountApi accountApi;
    public final PasswordRepository passwordRepository;
    public final ProfileRepository profileRepository;
    public final LearnApi learnApi;
    public final DictionaryRepository dictionaryRepository;
    public final ExternalDictionaryRepository externalDictionaryRepository;
    public final DeckRepository deckRepository;
    public final NotificationRepository notificationRepository;
    public final BadgeRepository badgeRepository;

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

        // Quên mật khẩu, hồ sơ đã có API (B1.3); huy hiệu vẫn chờ server. Cờ fake.* ghi đè trong local_config.properties
        accountApi = apiClient.create(AccountApi.class);
        passwordRepository = config.fakeForgotPassword
                ? new FakePasswordRepository(apiCalls)
                : new PasswordRepository.Remote(accountApi, apiCalls);
        profileRepository = config.fakeProfileUpdate
                ? new FakeProfileRepository(session, apiCalls)
                : new ProfileRepository.Remote(accountApi, apiCalls, session);

        learnApi = apiClient.create(LearnApi.class);
        dictionaryRepository = new DictionaryRepository(learnApi, apiCalls);
        externalDictionaryRepository = new ExternalDictionaryRepository(
                ApiClient.external(ExternalDictionaryApi.class, config, gson), apiCalls);
        deckRepository = new DeckRepository(learnApi, apiCalls);
        notificationRepository = new NotificationRepository(apiClient.create(NotificationApi.class), apiCalls, session);
        badgeRepository = config.fakeBadges
                ? new FakeBadgeRepository(context)
                : new BadgeRepository.Remote(apiClient.create(BadgeApi.class), apiCalls);
    }
}
