package com.example.nt118_project.ui.profile;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.AppContainer;
import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.UserDto;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hồ sơ người dùng: đọc từ phiên, làm mới bằng GET /auth/me và lưu thay đổi. */
public class ProfileViewModel extends ViewModel {

    private final AppContainer c = App.container();
    private final MutableLiveData<Resource<UserDto>> saveState = new MutableLiveData<>();
    private final MutableLiveData<Resource<UserDto>> uploadState = new MutableLiveData<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Bản nháp trong màn sửa hồ sơ, giữ qua xoay màn hình. */
    private boolean draftLoaded;
    private int draftTarget;
    @Nullable
    private String draftAvatarUrl;

    public LiveData<UserDto> user() {
        return c.session.currentUser();
    }

    public void refresh() {
        c.authRepository.refreshMe(r -> { });
    }

    public LiveData<Resource<UserDto>> saveState() {
        return saveState;
    }

    public void save(String fullName) {
        // Đang tải ảnh mà lưu thì PUT gửi URL ảnh cũ: server báo 422 hoặc xóa luôn ảnh vừa tải
        if (isLoading(saveState) || isUploading()) {
            return;
        }
        saveState.setValue(Resource.loading());
        c.profileRepository.update(fullName, draftTarget, draftAvatarUrl, saveState::postValue);
    }

    public void consumeSave() {
        saveState.setValue(null);
    }

    public LiveData<Resource<UserDto>> uploadState() {
        return uploadState;
    }

    /** Đọc, thu nhỏ ảnh trên thread nền rồi gửi POST /auth/me/avatar; thành công thì nháp dùng URL mới. */
    public void uploadAvatar(ContentResolver resolver, Uri uri) {
        if (isUploading() || isLoading(saveState)) {
            return;
        }
        uploadState.setValue(Resource.loading());
        io.execute(() -> {
            AvatarImage image;
            try {
                image = AvatarImage.prepare(resolver, uri);
            } catch (IOException | RuntimeException e) {
                uploadState.postValue(Resource.error(c.apiCalls.string(R.string.error_avatar_read), 0));
                return;
            }
            c.profileRepository.uploadAvatar(image.bytes, image.mimeType, r -> {
                if (r.isSuccess() && r.data != null) {
                    draftAvatarUrl = r.data.avatarUrl;
                }
                uploadState.postValue(r);
            });
        });
    }

    public boolean isUploading() {
        return isLoading(uploadState);
    }

    private static boolean isLoading(LiveData<Resource<UserDto>> live) {
        Resource<UserDto> r = live.getValue();
        return r != null && r.isLoading();
    }

    public void consumeUpload() {
        uploadState.setValue(null);
    }

    @Override
    protected void onCleared() {
        io.shutdown();
    }

    /** Lấy giá trị ban đầu từ người dùng hiện tại, chỉ một lần. Trả true nếu vừa nạp. */
    public boolean loadDraft(@Nullable UserDto user) {
        if (draftLoaded || user == null) {
            return false;
        }
        draftLoaded = true;
        draftTarget = user.targetScore != null ? user.targetScore : 450;
        draftAvatarUrl = user.avatarUrl;
        return true;
    }

    public int draftTarget() {
        return draftTarget;
    }

    public void setDraftTarget(int target) {
        draftTarget = target;
    }

    @Nullable
    public String draftAvatarUrl() {
        return draftAvatarUrl;
    }

    public void setDraftAvatarUrl(@Nullable String url) {
        draftAvatarUrl = url;
    }
}
