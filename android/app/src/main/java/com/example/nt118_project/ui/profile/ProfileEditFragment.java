package com.example.nt118_project.ui.profile;

import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.UserDto;
import com.example.nt118_project.databinding.FragmentProfileEditBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Sửa họ tên, mục tiêu Reading và avatar (URL HTTP(S)). Email chỉ hiển thị. */
public class ProfileEditFragment extends Fragment {

    private FragmentProfileEditBinding binding;
    private ProfileViewModel viewModel;
    private TextView[] goals;
    private static final int[] GOAL_VALUES = {300, 350, 400, 450};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        goals = new TextView[]{binding.goal300, binding.goal350, binding.goal400, binding.goal450};

        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnSaveTop.setOnClickListener(v -> save());
        binding.txtChangeAvatar.setOnClickListener(v -> showAvatarDialog());
        binding.avatarFrame.setOnClickListener(v -> showAvatarDialog());
        for (int i = 0; i < goals.length; i++) {
            int value = GOAL_VALUES[i];
            goals[i].setOnClickListener(v -> {
                viewModel.setDraftTarget(value);
                showGoal();
            });
        }

        UserDto user = viewModel.user().getValue();
        if (viewModel.loadDraft(user)) {
            binding.edtName.setText(user.fullName);
        }
        if (user != null) {
            binding.edtEmail.setText(user.email);
        }
        showGoal();
        showAvatar();

        viewModel.saveState().observe(getViewLifecycleOwner(), state -> {
            boolean loading = state != null && state.isLoading();
            binding.btnSave.setEnabled(!loading);
            binding.btnSaveTop.setEnabled(!loading);
            binding.btnSave.setText(loading ? R.string.profile_saving : R.string.profile_edit_09);
            if (state == null || loading) {
                return;
            }
            viewModel.consumeSave();
            if (state.isSuccess()) {
                Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show();
                nav.popBackStack();
            } else {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void save() {
        String name = binding.edtName.getText() == null ? "" : binding.edtName.getText().toString().trim();
        if (name.isEmpty() || name.length() > 150) {
            binding.edtName.setError(getString(R.string.error_name_required));
            binding.edtName.requestFocus();
            return;
        }
        viewModel.save(name);
    }

    private void showGoal() {
        int target = viewModel.draftTarget();
        for (int i = 0; i < goals.length; i++) {
            boolean on = GOAL_VALUES[i] == target;
            goals[i].setSelected(on);
            goals[i].setTextColor(ContextCompat.getColor(requireContext(),
                    on ? R.color.app_primary : R.color.app_text_secondary));
        }
        binding.txtGoalTitle.setText(getString(R.string.profile_goal_title, target));
        binding.txtGoalHint.setText(R.string.profile_goal_hint);
    }

    private void showAvatar() {
        AvatarBinder.bind(binding.avatarFrame, binding.txtInitials, binding.imgAvatar,
                binding.edtName.getText().toString(), viewModel.draftAvatarUrl());
    }

    /** Nhập URL ảnh HTTP(S); để trống hoặc bấm "Xóa ảnh" để dùng chữ cái đầu. */
    private void showAvatarDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(R.string.avatar_url_hint);
        input.setSingleLine(true);
        if (viewModel.draftAvatarUrl() != null) {
            input.setText(viewModel.draftAvatarUrl());
        }
        FrameLayout box = new FrameLayout(requireContext());
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.avatar_dialog_title)
                .setMessage(R.string.avatar_dialog_message)
                .setView(box)
                .setPositiveButton(R.string.avatar_apply, null)
                .setNeutralButton(R.string.avatar_remove, (d, w) -> {
                    viewModel.setDraftAvatarUrl(null);
                    showAvatar();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            if (url.isEmpty()) {
                viewModel.setDraftAvatarUrl(null);
            } else if (AvatarBinder.isHttpUrl(url)) {
                viewModel.setDraftAvatarUrl(url);
            } else {
                input.setError(getString(R.string.error_avatar_url));
                return;
            }
            showAvatar();
            dialog.dismiss();
        }));
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
