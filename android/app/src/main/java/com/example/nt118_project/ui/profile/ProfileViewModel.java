package com.example.nt118_project.ui.profile;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.AppContainer;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.UserDto;

/** Hồ sơ người dùng: đọc từ phiên, làm mới bằng GET /auth/me và lưu thay đổi. */
public class ProfileViewModel extends ViewModel {

    private final AppContainer c = App.container();
    private final MutableLiveData<Resource<UserDto>> saveState = new MutableLiveData<>();
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
        Resource<UserDto> current = saveState.getValue();
        if (current != null && current.isLoading()) {
            return;
        }
        saveState.setValue(Resource.loading());
        c.profileRepository.update(fullName, draftTarget, draftAvatarUrl, saveState::postValue);
    }

    public void consumeSave() {
        saveState.setValue(null);
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
