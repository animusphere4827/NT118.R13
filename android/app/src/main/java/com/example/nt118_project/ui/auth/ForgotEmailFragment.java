package com.example.nt118_project.ui.auth;

import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.databinding.FragmentForgotEmailBinding;

import java.util.Locale;

/** Quên mật khẩu bước 1: nhập email để nhận mã OTP. */
public class ForgotEmailFragment extends Fragment {

    static final String ARG_EMAIL = "email";
    static final String ARG_RESEND_AFTER = "resend_after";

    private FragmentForgotEmailBinding binding;
    private ForgotPasswordViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotEmailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);

        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnSend.setOnClickListener(v -> submit());

        viewModel.sendState().observe(getViewLifecycleOwner(), state -> {
            boolean loading = state != null && state.isLoading();
            binding.btnSend.setEnabled(!loading);
            binding.btnSend.setAlpha(loading ? 0.6f : 1f);
            binding.btnSend.setText(loading ? R.string.forgot_sending : R.string.forgot_email_06);
            if (state == null || loading) {
                return;
            }
            viewModel.consumeSend();
            if (state.isSuccess()) {
                Bundle args = new Bundle();
                args.putString(ARG_EMAIL, email());
                args.putInt(ARG_RESEND_AFTER, state.data != null ? state.data.resendAfter : 60);
                nav.navigate(R.id.forgot_otp, args);
            } else {
                Toast.makeText(requireContext(), state.code == 429
                        ? getString(R.string.error_too_many_requests) : state.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String email() {
        return binding.edtEmail.getText().toString().trim().toLowerCase(Locale.ROOT);
    }

    private void submit() {
        if (!Patterns.EMAIL_ADDRESS.matcher(email()).matches()) {
            binding.edtEmail.setError(getString(R.string.error_email_invalid));
            binding.edtEmail.requestFocus();
            return;
        }
        viewModel.sendOtp(email());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
