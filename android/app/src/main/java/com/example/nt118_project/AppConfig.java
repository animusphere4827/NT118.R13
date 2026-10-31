package com.example.nt118_project;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Cấu hình chạy app: địa chỉ API, Google Web Client ID và các cờ dùng dữ liệu giả.
 * Giá trị mặc định nằm ở đây; mỗi máy có thể ghi đè bằng file
 * app/src/main/assets/local_config.properties (đã đưa vào .gitignore, xem local_config.properties.example).
 */
public final class AppConfig {

    private static final String FILE = "local_config.properties";

    /** Emulator truy cập máy dev qua 10.0.2.2; server chạy cổng 3000. */
    public final String baseUrl;
    /** OAuth client ID loại Web dùng cho Credential Manager. Để trống trong repo. */
    public final String googleWebClientId;
    /** true = dùng FakeRepository vì server chưa có endpoint tương ứng. */
    public final boolean fakeGoogleAuth;
    public final boolean fakeForgotPassword;
    public final boolean fakeProfileUpdate;
    public final boolean fakeBadges;
    public final boolean debug;

    private AppConfig(Properties p, boolean debug) {
        String url = p.getProperty("api.baseUrl", "http://10.0.2.2:3000/api/").trim();
        baseUrl = url.endsWith("/") ? url : url + "/";
        googleWebClientId = p.getProperty("google.webClientId", "").trim();
        fakeGoogleAuth = flag(p, "fake.googleAuth", false);
        fakeForgotPassword = flag(p, "fake.forgotPassword", false);
        fakeProfileUpdate = flag(p, "fake.profileUpdate", false);
        fakeBadges = flag(p, "fake.badges", false);
        this.debug = debug;
    }

    public static AppConfig load(Context context) {
        Properties p = new Properties();
        try (InputStream in = context.getAssets().open(FILE)) {
            p.load(in);
        } catch (IOException ignored) {
            // Không có file ghi đè: dùng giá trị mặc định
        }
        if (p.getProperty("google.webClientId", "").trim().isEmpty()) {
            int clientId = context.getResources().getIdentifier("default_web_client_id", "string", context.getPackageName());
            if (clientId != 0) p.setProperty("google.webClientId", context.getString(clientId));
        }
        boolean debug = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        AppConfig config = new AppConfig(p, debug);
        if (debug) {
            Log.i("AppConfig", "baseUrl=" + config.baseUrl + ", googleClientId "
                    + (config.googleWebClientId.isEmpty() ? "trống" : "đã đặt"));
        }
        return config;
    }

    private static boolean flag(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }
}
