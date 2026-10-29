package com.example.nt118_project.ui.auth;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.databinding.FragmentForgotNewBinding;

import java.util.Locale;

/** Quên mật khẩu bước 3: đặt mật khẩu mới bằng reset_token nhận ở bước 2. */
public class ForgotNewPasswordFragment extends Fragment {

    private static final int MIN_PASSWORD = 12;

    private FragmentForgotNewBinding binding;
    private ForgotPasswordViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotNewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);
        String resetToken = requireArguments().getString(ForgotOtpFragment.ARG_RESET_TOKEN, "");

        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnToggleNew.setOnClickListener(v -> BaseAuthFragment.togglePassword(binding.edtNew));
        binding.btnToggleConfirm.setOnClickListener(v -> BaseAuthFragment.togglePassword(binding.edtConfirm));
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateRules();
            }
        };
        binding.edtNew.addTextChangedListener(watcher);
        binding.edtConfirm.addTextChangedListener(watcher);
        updateRules();

        binding.btnReset.setOnClickListener(v -> {
            if (updateRules()) {
                viewModel.reset(resetToken, value(binding.edtNew));
            } else {
                Toast.makeText(requireContext(), R.string.error_password_rules, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.resetState().observe(getViewLifecycleOwner(), state -> {
            boolean loading = state != null && state.isLoading();
            binding.btnReset.setEnabled(!loading);
            binding.btnReset.setText(loading ? R.string.forgot_resetting : R.string.forgot_new_11);
            if (state == null || loading) {
                return;
            }
            viewModel.consumeReset();
            if (state.isSuccess()) {
                Toast.makeText(requireContext(), R.string.forgot_done, Toast.LENGTH_LONG).show();
                // Quay về màn đăng nhập đang nằm dưới 3 bước quên mật khẩu
                if (!nav.popBackStack(R.id.login, false)) {
                    nav.navigate(R.id.login);
                }
            } else {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Tô xanh từng điều kiện đã đạt. Bắt buộc giống lúc đăng ký và giống server: 12 đến 128 ký tự
     * và hai lần nhập trùng nhau; chữ hoa/thường và chữ số chỉ là khuyến nghị. Trả true khi đủ điều kiện bắt buộc.
     */
    private boolean updateRules() {
        String pw = value(binding.edtNew);
        String confirm = value(binding.edtConfirm);
        boolean[] ok = {
                pw.length() >= MIN_PASSWORD && pw.length() <= 128,
                !pw.equals(pw.toLowerCase(Locale.ROOT)) && !pw.equals(pw.toUpperCase(Locale.ROOT)),
                pw.matches(".*\\d.*"),
                !pw.isEmpty() && pw.equals(confirm)
        };
        ImageView[] icons = {binding.rule1, binding.rule2, binding.rule3, binding.rule4};
        for (int i = 0; i < ok.length; i++) {
            int color = ContextCompat.getColor(requireContext(), ok[i] ? R.color.app_success : R.color.app_text_secondary);
            ImageViewCompat.setImageTintList(icons[i], ColorStateList.valueOf(color));
            icons[i].setAlpha(ok[i] ? 1f : 0.5f);
        }
        boolean all = ok[0] && ok[3];
        binding.btnReset.setAlpha(all ? 1f : 0.6f);
        return all;
    }

    private static String value(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
