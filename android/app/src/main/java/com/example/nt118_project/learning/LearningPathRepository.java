package com.example.nt118_project.learning;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Nguồn dữ liệu lộ trình. Bản thật sẽ gọi API Course (B2.1, chờ TV1);
 * hiện có MockLearningPathRepository bật bằng BuildConfig.TV3_MOCK_LEARNING_PATH.
 */
public interface LearningPathRepository {

    /** Callback luôn được gọi trên main thread. */
    interface Callback {
        void onSuccess(@NonNull List<PathUnit> units);

        void onError(@NonNull String message);
    }

    /** Trả về đối tượng hủy được; hủy rồi thì callback không được gọi nữa. */
    @NonNull
    Cancellable load(@NonNull Callback callback);

    /** True nếu dữ liệu là giả lập, giao diện phải gắn nhãn cho người dùng thấy. */
    boolean isMock();

    interface Cancellable {
        void cancel();
    }
}
