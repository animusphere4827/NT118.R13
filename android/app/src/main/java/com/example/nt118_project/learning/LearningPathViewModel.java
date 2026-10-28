package com.example.nt118_project.learning;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.BuildConfig;

import java.util.List;

/**
 * Trạng thái lộ trình dùng chung cho Trang chủ và màn Lộ trình (scope theo Activity),
 * nên chuyển tab không tải lại và hai màn luôn hiển thị cùng một tiến độ.
 */
public class LearningPathViewModel extends ViewModel {

    /** Trạng thái màn hình: đang tải, lỗi (kèm thông báo) hoặc có dữ liệu. */
    public static final class UiState {
        public final boolean loading;
        @Nullable
        public final String error;
        @Nullable
        public final List<PathUnit> units;
        @Nullable
        public final PathUnlockPolicy.Result progress;
        public final boolean mock;

        UiState(boolean loading, @Nullable String error, @Nullable List<PathUnit> units, boolean mock) {
            this.loading = loading;
            this.error = error;
            this.units = units;
            this.progress = units == null ? null : PathUnlockPolicy.evaluate(units);
            this.mock = mock;
        }

        public boolean isEmpty() {
            return progress != null && progress.totalCount() == 0;
        }
    }

    private final LearningPathRepository repository;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    @Nullable
    private LearningPathRepository.Cancellable pending;

    public LearningPathViewModel() {
        this(BuildConfig.TV3_MOCK_LEARNING_PATH
                ? new MockLearningPathRepository()
                : new UnavailableLearningPathRepository());
    }

    LearningPathViewModel(@NonNull LearningPathRepository repository) {
        this.repository = repository;
    }

    @NonNull
    public LiveData<UiState> state() {
        if (state.getValue() == null) {
            load();
        }
        return state;
    }

    /** Tải (lại) dữ liệu; bỏ qua nếu đang tải để chạm "Thử lại" liên tục không gọi trùng. */
    public void load() {
        UiState current = state.getValue();
        if (current != null && current.loading) {
            return;
        }
        state.setValue(new UiState(true, null, current == null ? null : current.units, repository.isMock()));
        pending = repository.load(new LearningPathRepository.Callback() {
            @Override
            public void onSuccess(@NonNull List<PathUnit> units) {
                pending = null;
                state.setValue(new UiState(false, null, units, repository.isMock()));
            }

            @Override
            public void onError(@NonNull String message) {
                pending = null;
                state.setValue(new UiState(false, message, null, repository.isMock()));
            }
        });
    }

    @Override
    protected void onCleared() {
        if (pending != null) {
            pending.cancel();
        }
        if (repository instanceof MockLearningPathRepository) {
            ((MockLearningPathRepository) repository).shutdown();
        }
    }
}
