package com.example.nt118_project.data.local;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.nt118_project.common.Event;
import com.example.nt118_project.data.remote.dto.UserDto;
import com.google.gson.Gson;

/**
 * Trạng thái phiên đăng nhập của cả app. Khi không còn refresh được access token
 * (xem TokenAuthenticator), phiên bị xóa và {@link #sessionExpired()} phát sự kiện để MainActivity
 * đưa người dùng về màn đăng nhập.
 */
public class SessionManager {

    /** Token do FakeRepository cấp khi server chưa có endpoint; không gửi lên server. */
    public static final String FAKE_TOKEN_PREFIX = "fake-";

    private final TokenStore store;
    private final Gson gson;
    private final MutableLiveData<Event<Boolean>> expired = new MutableLiveData<>();
    private final MutableLiveData<UserDto> currentUser = new MutableLiveData<>();

    public SessionManager(TokenStore store, Gson gson) {
        this.store = store;
        this.gson = gson;
        currentUser.setValue(readUser());
    }

    @Nullable
    public String token() {
        return store.getToken();
    }

    @Nullable
    public String refreshToken() {
        return store.getRefreshToken();
    }

    public boolean isLoggedIn() {
        return store.hasToken();
    }

    public boolean isFakeSession() {
        String token = store.getToken();
        return token != null && token.startsWith(FAKE_TOKEN_PREFIX);
    }

    /** Lưu phiên sau khi đăng nhập thành công. */
    public void start(String token, @Nullable String refreshToken, @Nullable UserDto user) {
        store.saveTokens(token, refreshToken);
        updateUser(user);
    }

    /** Thay cặp token sau POST /auth/refresh; refresh token cũ không dùng lại được nữa. */
    public void updateTokens(String token, String refreshToken) {
        store.saveTokens(token, refreshToken);
    }

    public void updateUser(@Nullable UserDto user) {
        if (user != null) {
            store.saveUserJson(gson.toJson(user));
        }
        currentUser.postValue(user);
    }

    public LiveData<UserDto> currentUser() {
        return currentUser;
    }

    /** Đăng xuất chủ động: chỉ xóa dữ liệu, không phát sự kiện hết phiên. */
    public void clear() {
        store.clear();
        currentUser.postValue(null);
    }

    /** Gọi từ TokenAuthenticator khi refresh token hết hạn, bị thu hồi hoặc không có. */
    public void expire() {
        if (!store.hasToken()) {
            return;
        }
        clear();
        expired.postValue(new Event<>(true));
    }

    public LiveData<Event<Boolean>> sessionExpired() {
        return expired;
    }

    @Nullable
    private UserDto readUser() {
        String json = store.getUserJson();
        if (json == null) {
            return null;
        }
        try {
            return gson.fromJson(json, UserDto.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
