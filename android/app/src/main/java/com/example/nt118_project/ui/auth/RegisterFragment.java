package com.example.nt118_project.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.AuthResponse;
import com.example.nt118_project.databinding.FragmentRegisterBinding;

import java.util.Locale;

/** Đăng ký (POST /auth/register rồi tự đăng nhập) hoặc tiếp tục với Google. */
public class RegisterFragment extends BaseAuthFragment {

    private FragmentRegisterBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        NavController nav = NavHostFragment.findNavController(this);

        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnTogglePassword.setOnClickListener(v -> togglePassword(binding.edtPassword));
        binding.btnRegister.setOnClickListener(v -> submit());
        binding.btnGoogle.setOnClickListener(v -> startGoogleSignIn());
        binding.btnToLogin.setOnClickListener(v -> {
            // Đi từ đăng nhập sang thì quay lại, còn đi từ giới thiệu thì mở màn đăng nhập
            if (!nav.popBackStack(R.id.login, false)) {
                nav.navigate(R.id.login);
            }
        });
        binding.edtPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showStrength(s.toString());
            }
        });
        showStrength(binding.edtPassword.getText().toString());
    }

    private void submit() {
        String name = text(binding.edtName);
        if (name.isEmpty() || name.length() > 150) {
            binding.edtName.setError(getString(R.string.error_name_required));
            binding.edtName.requestFocus();
            return;
        }
        if (!validEmail(binding.edtEmail) || !validPassword(binding.edtPassword)) {
            return;
        }
        if (!binding.chkTerms.isChecked()) {
            Toast.makeText(requireContext(), R.string.error_terms_required, Toast.LENGTH_SHORT).show();
            return;
        }
        viewModel.register(name, text(binding.edtEmail).toLowerCase(Locale.ROOT),
                binding.edtPassword.getText().toString());
    }

    /** 0 đến 4 vạch: đủ 12 ký tự, có hoa và thường, có số, có ký tự đặc biệt. */
    private void showStrength(String password) {
        int score = 0;
        if (password.length() >= MIN_PASSWORD) score++;
        if (!password.equals(password.toLowerCase(Locale.ROOT)) && !password.equals(password.toUpperCase(Locale.ROOT))) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;
        if (password.length() < MIN_PASSWORD) score = Math.min(score, 1);

        int on = ContextCompat.getColor(requireContext(), score >= 3 ? R.color.app_success : R.color.app_orange);
        int off = ContextCompat.getColor(requireContext(), R.color.app_border);
        View[] bars = {binding.strength1, binding.strength2, binding.strength3, binding.strength4};
        for (int i = 0; i < bars.length; i++) {
            bars[i].setBackgroundColor(i < score ? on : off);
        }
        binding.txtStrength.setTextColor(password.isEmpty()
                ? ContextCompat.getColor(requireContext(), R.color.app_text_secondary) : on);
        binding.txtStrength.setText(password.length() < MIN_PASSWORD
                ? getString(R.string.password_hint_min, MIN_PASSWORD)
                : getString(score >= 3 ? R.string.password_strong : R.string.password_medium));
    }

    @Override
    protected String errorMessage(Resource<AuthResponse> state) {
        if (state.code == 409) {
            return getString(R.string.error_email_exists);
        }
        return super.errorMessage(state);
    }

    @Override
    protected void showLoading(boolean loading) {
        binding.btnRegister.setEnabled(!loading);
        binding.btnGoogle.setEnabled(!loading);
        binding.btnRegister.setText(loading ? R.string.auth_registering : R.string.register_09);
        binding.btnRegister.setAlpha(loading ? 0.6f : 1f);
        binding.btnGoogle.setAlpha(loading ? 0.6f : 1f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
