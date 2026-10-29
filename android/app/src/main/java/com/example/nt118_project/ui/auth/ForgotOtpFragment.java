package com.example.nt118_project.ui.auth;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.data.repository.fake.FakePasswordRepository;
import com.example.nt118_project.databinding.FragmentForgotOtpBinding;

import java.util.Locale;

/** Quên mật khẩu bước 2: nhập mã 6 số, đếm ngược trước khi cho gửi lại. */
public class ForgotOtpFragment extends Fragment {

    static final String ARG_RESET_TOKEN = "reset_token";
    private static final int OTP_LENGTH = 6;

    private FragmentForgotOtpBinding binding;
    private ForgotPasswordViewModel viewModel;
    private CountDownTimer timer;
    private String email;
    private TextView[] boxes;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotOtpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);
        Bundle args = requireArguments();
        email = args.getString(ForgotEmailFragment.ARG_EMAIL, "");
        if (viewModel.markOtpScreenStarted()) {
            viewModel.startResendTimer(args.getInt(ForgotEmailFragment.ARG_RESEND_AFTER, 60));
            if (App.container().config.fakeForgotPassword) {
                Toast.makeText(requireContext(), getString(R.string.forgot_fake_hint, FakePasswordRepository.OTP),
                        Toast.LENGTH_LONG).show();
            }
        }

        boxes = new TextView[]{binding.otp1, binding.otp2, binding.otp3, binding.otp4, binding.otp5, binding.otp6};
        binding.txtSentTo.setText(getString(R.string.forgot_sent_to, email));
        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnVerify.setOnClickListener(v -> submit());
        binding.edtOtp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showDigits(s.toString());
                if (s.length() == OTP_LENGTH && viewModel.shouldAutoSubmit(s.toString())) {
                    submit();
                }
            }
        });
        showDigits(binding.edtOtp.getText().toString());
        binding.edtOtp.requestFocus();
        binding.edtOtp.post(() -> {
            InputMethodManager imm = ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
            if (imm != null && binding != null) {
                imm.showSoftInput(binding.edtOtp, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        binding.txtResend.setOnClickListener(v -> {
            if (viewModel.resendRemainingMs() == 0) {
                viewModel.sendOtp(email);
            }
        });
        viewModel.sendState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || state.isLoading()) {
                return;
            }
            viewModel.consumeSend();
            Toast.makeText(requireContext(), state.isSuccess() ? getString(R.string.forgot_resent) : state.message,
                    Toast.LENGTH_SHORT).show();
            startCountdown();
        });
        viewModel.verifyState().observe(getViewLifecycleOwner(), state -> {
            boolean loading = state != null && state.isLoading();
            binding.btnVerify.setEnabled(!loading);
            binding.btnVerify.setAlpha(loading ? 0.6f : 1f);
            binding.btnVerify.setText(loading ? R.string.forgot_verifying : R.string.forgot_otp_12);
            if (state == null || loading) {
                return;
            }
            viewModel.consumeVerify();
            if (state.isSuccess() && state.data != null) {
                Bundle next = new Bundle();
                next.putString(ARG_RESET_TOKEN, state.data.resetToken);
                nav.navigate(R.id.forgot_new, next);
            } else {
                Toast.makeText(requireContext(), state.code == 429
                        ? getString(R.string.error_too_many_requests) : state.message, Toast.LENGTH_LONG).show();
                viewModel.clearAutoSubmitted();
                binding.edtOtp.setText("");
            }
        });
        startCountdown();
    }

    private void submit() {
        String otp = binding.edtOtp.getText().toString();
        if (otp.length() != OTP_LENGTH) {
            Toast.makeText(requireContext(), R.string.error_otp_length, Toast.LENGTH_SHORT).show();
            return;
        }
        viewModel.verify(email, otp);
    }

    /** Ô kế tiếp cần nhập được viền xanh như thiết kế. */
    private void showDigits(String otp) {
        for (int i = 0; i < boxes.length; i++) {
            boxes[i].setText(i < otp.length() ? String.valueOf(otp.charAt(i)) : "");
            boolean current = i == Math.min(otp.length(), OTP_LENGTH - 1);
            boxes[i].setBackgroundResource(current ? R.drawable.bg_field_focus : R.drawable.bg_field);
        }
    }

    private void startCountdown() {
        if (timer != null) {
            timer.cancel();
        }
        long remaining = viewModel.resendRemainingMs();
        if (remaining == 0) {
            showResendReady();
            return;
        }
        timer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long ms) {
                long s = (ms + 999) / 1000;
                binding.txtResend.setText(getString(R.string.forgot_resend_in,
                        String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)));
                binding.txtResend.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_text_secondary));
            }

            @Override
            public void onFinish() {
                showResendReady();
            }
        }.start();
    }

    private void showResendReady() {
        if (binding == null) {
            return;
        }
        binding.txtResend.setText(R.string.forgot_resend);
        binding.txtResend.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_primary));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) {
            timer.cancel();
        }
        binding = null;
    }
}
