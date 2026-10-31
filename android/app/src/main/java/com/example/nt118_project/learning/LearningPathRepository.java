package com.example.nt118_project.learning;

import androidx.annotation.NonNull;

import java.util.List;

public interface LearningPathRepository {

    interface Callback {
        void onSuccess(@NonNull List<PathUnit> units);

        void onError(@NonNull String message);
    }

    @NonNull
    Cancellable load(@NonNull Callback callback);

    boolean isMock();

    interface Cancellable {
        void cancel();
    }
}
