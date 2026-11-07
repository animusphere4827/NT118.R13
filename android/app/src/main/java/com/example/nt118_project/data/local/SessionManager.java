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

    public static final class Snapshot {
        public final long generation;
        public final String token;
        public final String refreshToken;
        private Snapshot(long generation, String token, String refreshToken) {
            this.generation = generation;
            this.token = token;
            this.refreshToken = refreshToken;
        }
    }
    private long generation;
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
    public synchronized String token() {
        return store.getToken();
    }

    @Nullable
    public synchronized String refreshToken() {
        return store.getRefreshToken();
    }

    public synchronized boolean isLoggedIn() {
        return store.hasToken();
    }

    public synchronized boolean isFakeSession() {
        String token = store.getToken();
        return token != null && token.startsWith(FAKE_TOKEN_PREFIX);
    }

    /** Lưu phiên sau khi đăng nhập thành công. */
    public synchronized void start(String token, @Nullable String refreshToken, @Nullable UserDto user) {
        generation++;
        store.clear();
        store.saveTokens(token, refreshToken);
        updateUser(user);
    }

    public synchronized void updateUser(@Nullable UserDto user) {
        if (user != null) {
            store.saveUserJson(gson.toJson(user));
        }
        currentUser.postValue(user);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(generation, store.getToken(), store.getRefreshToken());
    }
    public synchronized Snapshot snapshot(Snapshot expected) {
        return matches(expected) ? snapshot() : null;
    }

    public synchronized boolean matches(Snapshot expected) {
        return expected != null && expected.generation == generation && store.hasToken();
    }
    public synchronized boolean refresh(Snapshot expected, String token, String refreshToken, UserDto user) {
        if (!matches(expected) || !java.util.Objects.equals(expected.refreshToken, store.getRefreshToken())) return false;
        store.saveTokens(token, refreshToken);
        if (user != null) updateUser(user);
        return true;
    }
    public synchronized void updateUser(Snapshot expected, UserDto user) {
        if (matches(expected)) updateUser(user);
    }
    public synchronized void expire(Snapshot expected) {
        if (matches(expected)) expire();
    }
    public LiveData<UserDto> currentUser() {
        return currentUser;
    }

    /** Đăng xuất chủ động: chỉ xóa dữ liệu, không phát sự kiện hết phiên. */
    public synchronized void clear() {
        generation++;
        store.clear();
        currentUser.postValue(null);
    }

    /** Gọi từ TokenAuthenticator khi refresh token hết hạn, bị thu hồi hoặc không có. */
    public synchronized void expire() {
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
