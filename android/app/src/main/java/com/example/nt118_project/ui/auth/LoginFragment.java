package com.example.nt118_project.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.databinding.FragmentLoginBinding;

/** Đăng nhập email/mật khẩu (POST /auth/login) hoặc Google (POST /auth/firebase). */
public class LoginFragment extends BaseAuthFragment {

    private FragmentLoginBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        NavController nav = NavHostFragment.findNavController(this);

        // Màn đầu tiên của back stack (sau khi đăng xuất, hết phiên) thì không có gì để quay lại
        binding.btnBack.setVisibility(nav.getPreviousBackStackEntry() == null ? View.INVISIBLE : View.VISIBLE);
        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnTogglePassword.setOnClickListener(v -> togglePassword(binding.edtPassword));
        binding.edtPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        binding.btnLogin.setOnClickListener(v -> submit());
        binding.btnGoogle.setOnClickListener(v -> startGoogleSignIn());
        binding.btnToRegister.setOnClickListener(v -> nav.navigate(R.id.register));
        binding.txtForgot.setOnClickListener(v -> nav.navigate(R.id.forgot_email));
    }

    private void submit() {
        if (!validEmail(binding.edtEmail) || !validPassword(binding.edtPassword)) {
            return;
        }
        viewModel.login(text(binding.edtEmail).toLowerCase(java.util.Locale.ROOT),
                binding.edtPassword.getText().toString());
    }

    @Override
    protected void showLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnGoogle.setEnabled(!loading);
        binding.btnLogin.setText(loading ? R.string.auth_logging_in : R.string.login_action);
        binding.btnLogin.setAlpha(loading ? 0.6f : 1f);
        binding.btnGoogle.setAlpha(loading ? 0.6f : 1f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
