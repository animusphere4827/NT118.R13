package com.example.nt118_project.ui.profile;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.BadgeDto;
import com.example.nt118_project.databinding.FragmentProfileBinding;
import com.example.nt118_project.ui.ScreenFragment;

/**
 * Màn Cá nhân. Các dòng menu, đăng xuất và thanh tab vẫn nối bằng android:tag qua {@link ScreenFragment};
 * lớp này chỉ điền họ tên, email, mục tiêu và avatar của người dùng.
 */
public class ProfileFragment extends ScreenFragment {

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentProfileBinding binding = FragmentProfileBinding.bind(view);
        ProfileViewModel viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        viewModel.user().observe(getViewLifecycleOwner(), user -> {
            if (user == null) {
                return;
            }
            binding.txtName.setText(user.fullName);
            binding.txtEmail.setText(user.email);
            binding.txtTarget.setText(getString(R.string.profile_target_format,
                    user.targetScore != null ? user.targetScore : 450));
            AvatarBinder.bind(binding.avatarFrame, binding.txtInitials, binding.imgAvatar, user.fullName, user.avatarUrl);
        });
        if (savedInstanceState == null) {
            viewModel.refresh();
        }
        App.container().badgeRepository.badges(r -> {
            if (!isAdded() || getView() == null || !r.isSuccess() || r.data == null) {
                return;
            }
            binding.txtBadgeSummary.setText(getString(R.string.profile_badge_summary, r.data.unlockedCount));
            String featured = null;
            for (BadgeDto b : r.data.rows) {
                if (b.code != null && b.code.equals(r.data.featuredCode)) {
                    featured = b.name;
                }
            }
            binding.txtFeaturedBadge.setVisibility(featured == null ? View.GONE : View.VISIBLE);
            binding.txtFeaturedBadge.setText(featured == null ? null : getString(R.string.profile_featured_badge, featured));
        });
    }
}
