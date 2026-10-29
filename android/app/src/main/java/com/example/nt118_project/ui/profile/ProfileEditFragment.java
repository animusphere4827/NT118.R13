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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.UserDto;
import com.example.nt118_project.databinding.FragmentProfileEditBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Sửa họ tên, mục tiêu Reading và avatar (ảnh từ thư viện hoặc URL HTTP(S)). Email chỉ hiển thị. */
public class ProfileEditFragment extends Fragment {

    private FragmentProfileEditBinding binding;
    private ProfileViewModel viewModel;
    private TextView[] goals;
    private static final int[] GOAL_VALUES = {300, 350, 400, 450};

    /** Photo Picker hệ thống: không cần quyền đọc bộ nhớ. */
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    viewModel.uploadAvatar(requireContext().getContentResolver(), uri);
                }
            });

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
        binding.txtChangeAvatar.setOnClickListener(v -> showAvatarOptions());
        binding.avatarFrame.setOnClickListener(v -> showAvatarOptions());
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
            updateSaveEnabled();
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

        viewModel.uploadState().observe(getViewLifecycleOwner(), state -> {
            boolean loading = state != null && state.isLoading();
            binding.txtChangeAvatar.setEnabled(!loading);
            binding.avatarFrame.setEnabled(!loading);
            updateSaveEnabled();
            binding.txtChangeAvatar.setText(loading ? R.string.avatar_uploading : R.string.profile_edit_05);
            if (state == null || loading) {
                return;
            }
            viewModel.consumeUpload();
            if (state.isSuccess()) {
                showAvatar();
                Toast.makeText(requireContext(), R.string.avatar_uploaded, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Khóa nút Lưu khi đang lưu hoặc đang tải ảnh đại diện lên. */
    private void updateSaveEnabled() {
        Resource<UserDto> saving = viewModel.saveState().getValue();
        boolean enabled = !(saving != null && saving.isLoading()) && !viewModel.isUploading();
        binding.btnSave.setEnabled(enabled);
        binding.btnSaveTop.setEnabled(enabled);
        binding.btnSave.setAlpha(enabled ? 1f : 0.6f);
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

    /** Ảnh từ thư viện được tải lên ngay; URL và "Xóa ảnh" chỉ đổi bản nháp, áp dụng khi bấm Lưu. */
    private void showAvatarOptions() {
        boolean hasAvatar = viewModel.draftAvatarUrl() != null;
        String[] items = hasAvatar
                ? new String[]{getString(R.string.avatar_pick_gallery), getString(R.string.avatar_enter_url), getString(R.string.avatar_remove)}
                : new String[]{getString(R.string.avatar_pick_gallery), getString(R.string.avatar_enter_url)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.avatar_dialog_title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        pickImage.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    } else if (which == 1) {
                        showAvatarDialog();
                    } else {
                        viewModel.setDraftAvatarUrl(null);
                        showAvatar();
                    }
                })
                .show();
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
