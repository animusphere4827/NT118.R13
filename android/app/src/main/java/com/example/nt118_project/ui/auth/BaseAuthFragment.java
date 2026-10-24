package com.example.nt118_project.ui.auth;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.App;
import com.example.nt118_project.AppConfig;
import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.AuthResponse;

/** Phần chung của màn đăng nhập và đăng ký: đăng nhập Google, theo dõi trạng thái, chuyển vào trang chủ. */
public abstract class BaseAuthFragment extends Fragment {

    protected AuthViewModel viewModel;

    /** Bật/tắt các nút khi đang gửi request. */
    protected abstract void showLoading(boolean loading);

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        // Đã tới màn tài khoản thì lần sau mở app không cần xem lại giới thiệu
        App.container().appPrefs.setOnboardingDone();
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(@Nullable Resource<AuthResponse> state) {
        showLoading(state != null && state.isLoading());
        if (state == null || state.isLoading()) {
            return;
        }
        if (state.isSuccess()) {
            viewModel.consume();
            onLoggedIn(state.data);
        } else {
            String message = errorMessage(state);
            viewModel.consume();
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    /** Màn hình con có thể đổi câu báo lỗi theo mã HTTP (ví dụ 409 khi đăng ký). */
    protected String errorMessage(Resource<AuthResponse> state) {
        if (state.code == 429) {
            return getString(R.string.error_too_many_requests);
        }
        return state.message != null ? state.message : getString(R.string.error_unknown);
    }

    protected void onLoggedIn(@Nullable AuthResponse response) {
        if (response != null && response.isNewUser) {
            Toast.makeText(requireContext(), R.string.auth_welcome_new, Toast.LENGTH_SHORT).show();
        }
        NavHostFragment.findNavController(this).navigate(R.id.home, null,
                new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build());
    }

    /** Lấy ID token qua Credential Manager rồi đổi lấy JWT. Khi bật cờ giả mà chưa có client ID thì bỏ qua bước chọn tài khoản. */
    protected void startGoogleSignIn() {
        if (viewModel.isBusy()) {
            return;
        }
        AppConfig config = App.container().config;
        if (config.googleWebClientId.isEmpty()) {
            if (config.fakeGoogleAuth) {
                viewModel.loginWithGoogle(null);
            } else {
                Toast.makeText(requireContext(), R.string.error_google_not_configured, Toast.LENGTH_LONG).show();
            }
            return;
        }
        viewModel.googleStarted();
        GoogleSignInHelper.signIn(requireActivity(), config.googleWebClientId, new GoogleSignInHelper.Callback() {
            @Override
            public void onIdToken(String idToken) {
                viewModel.loginWithGoogle(idToken);
            }

            @Override
            public void onCancelled() {
                viewModel.googleFailed(null);
            }

            @Override
            public void onError(String message) {
                viewModel.googleFailed(message);
            }
        });
    }

    protected static void togglePassword(EditText edit) {
        boolean hidden = (edit.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (hidden
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_TEXT_VARIATION_PASSWORD));
        edit.setSelection(edit.length());
    }

    protected static String text(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    protected boolean validEmail(EditText edit) {
        String email = text(edit);
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edit.setError(getString(R.string.error_email_invalid));
            edit.requestFocus();
            return false;
        }
        return true;
    }

    /** Server yêu cầu mật khẩu 12 đến 128 ký tự. */
    protected boolean validPassword(EditText edit) {
        int length = edit.getText() == null ? 0 : edit.getText().length();
        if (length < MIN_PASSWORD || length > 128) {
            edit.setError(getString(R.string.error_password_length, MIN_PASSWORD));
            edit.requestFocus();
            return false;
        }
        return true;
    }

    protected static final int MIN_PASSWORD = 12;
}
