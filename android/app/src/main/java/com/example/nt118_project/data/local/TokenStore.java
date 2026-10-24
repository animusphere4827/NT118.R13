package com.example.nt118_project.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Lưu JWT và thông tin người dùng hiện tại trong EncryptedSharedPreferences.
 * File "secure_session" đã được loại khỏi sao lưu (xem res/xml/backup_rules.xml).
 */
public class TokenStore {

    private static final String TAG = "TokenStore";
    private static final String FILE = "secure_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER = "user_json";

    private final SharedPreferences prefs;

    public TokenStore(Context context) {
        this.prefs = open(context.getApplicationContext());
    }

    private static SharedPreferences open(Context context) {
        try {
            return create(context);
        } catch (Exception first) {
            // Khóa trong Keystore bị mất (cài lại, khôi phục máy): xóa file cũ rồi tạo lại, người dùng chỉ cần đăng nhập lại
            Log.w(TAG, "Không mở được kho mã hóa, tạo lại", first);
            context.deleteSharedPreferences(FILE);
            try {
                return create(context);
            } catch (Exception second) {
                throw new IllegalStateException("Không khởi tạo được kho lưu phiên", second);
            }
        }
    }

    private static SharedPreferences create(Context context) throws Exception {
        MasterKey key = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(context, FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    @Nullable
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public boolean hasToken() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    @Nullable
    public String getUserJson() {
        return prefs.getString(KEY_USER, null);
    }

    public void saveUserJson(String json) {
        prefs.edit().putString(KEY_USER, json).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
