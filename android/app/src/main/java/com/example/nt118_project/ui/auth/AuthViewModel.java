package com.example.nt118_project.ui.auth;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.AuthResponse;
import com.example.nt118_project.data.repository.AuthRepository;

/** Trạng thái đăng nhập/đăng ký dùng chung cho LoginFragment và RegisterFragment. */
public class AuthViewModel extends ViewModel {

    private final AuthRepository repository = App.container().authRepository;
    private final MutableLiveData<Resource<AuthResponse>> state = new MutableLiveData<>();

    /** null = chưa làm gì; LOADING / SUCCESS / ERROR. Màn hình gọi {@link #consume()} sau khi xử lý xong. */
    public LiveData<Resource<AuthResponse>> state() {
        return state;
    }

    public boolean isBusy() {
        Resource<AuthResponse> r = state.getValue();
        return r != null && r.isLoading();
    }

    public void login(String email, String password) {
        if (isBusy()) {
            return;
        }
        state.setValue(Resource.loading());
        repository.login(email, password, state::postValue);
    }

    public void register(String fullName, String email, String password) {
        if (isBusy()) {
            return;
        }
        state.setValue(Resource.loading());
        repository.register(fullName, email, password, state::postValue);
    }

    /** Bắt đầu chờ Credential Manager trả ID token. */
    public void googleStarted() {
        state.setValue(Resource.loading());
    }

    public void loginWithGoogle(@Nullable String idToken) {
        state.setValue(Resource.loading());
        repository.loginWithGoogle(idToken, state::postValue);
    }

    public void googleFailed(@Nullable String message) {
        state.setValue(message == null ? null : Resource.error(message, 0));
    }

    public void consume() {
        state.setValue(null);
    }
}
