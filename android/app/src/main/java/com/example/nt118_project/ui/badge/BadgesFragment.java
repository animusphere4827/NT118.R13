package com.example.nt118_project.ui.badge;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.BadgeDto;
import com.example.nt118_project.data.remote.dto.BadgeListResponse;
import com.example.nt118_project.databinding.FragmentBadgesBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Bộ sưu tập huy hiệu: chạm huy hiệu đã mở để xem màn chúc mừng, huy hiệu chưa mở để xem điều kiện. */
public class BadgesFragment extends Fragment {

    private FragmentBadgesBinding binding;
    private final MutableLiveData<Resource<BadgeListResponse>> state = new MutableLiveData<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBadgesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        BadgeAdapter adapter = new BadgeAdapter(badge -> {
            if (badge.unlocked) {
                nav.navigate(R.id.badge_congrats, BadgeCongratsFragment.args(badge));
            } else {
                Toast.makeText(requireContext(), getString(R.string.badge_locked_hint, badge.description,
                        badge.progress, badge.target), Toast.LENGTH_LONG).show();
            }
        });
        binding.rvBadges.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.rvBadges.setAdapter(adapter);
        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnInfo.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.badge_info_title)
                .setMessage(R.string.badge_info_message)
                .setPositiveButton(android.R.string.ok, null)
                .show());

        state.observe(getViewLifecycleOwner(), r -> {
            if (r == null || r.isLoading()) {
                showStatus(getString(R.string.loading));
                return;
            }
            if (r.isError() || r.data == null) {
                showStatus(r.message);
                return;
            }
            showStatus(null);
            adapter.submitList(r.data.rows);
            int total = Math.max(r.data.total, r.data.rows.size());
            binding.txtUnlocked.setText(String.valueOf(r.data.unlockedCount));
            binding.progressBadges.setProgress(total == 0 ? 0 : 100 * r.data.unlockedCount / total);
            binding.txtUnlockedSummary.setText(getString(R.string.badge_summary, r.data.unlockedCount, total));
        });
        state.setValue(Resource.loading());
        App.container().badgeRepository.badges(state::postValue);
    }

    private void showStatus(@Nullable String message) {
        binding.txtStatus.setVisibility(message == null ? View.GONE : View.VISIBLE);
        binding.txtStatus.setText(message);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
