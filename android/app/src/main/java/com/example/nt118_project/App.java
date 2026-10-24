package com.example.nt118_project;

import android.app.Application;
import android.util.Log;

public class App extends Application {

    private static AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
        if (container.config.debug) {
            // Kiểm tra nhanh kết nối tới server khi chạy bản debug, xem kết quả trong Logcat với tag "Api"
            container.apiCalls.enqueue(container.authApi.health(), r -> Log.i("Api",
                    "GET " + container.config.baseUrl + "health: " + (r.isSuccess() ? "OK" : r.message)));
        }
    }

    public static AppContainer container() {
        return container;
    }
}
