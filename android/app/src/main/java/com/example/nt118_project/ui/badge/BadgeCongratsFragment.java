package com.example.nt118_project.ui.badge;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.data.remote.dto.BadgeDto;
import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.databinding.FragmentBadgeCongratsBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Chúc mừng huy hiệu: pháo giấy Lottie, huy hiệu phóng to, chia sẻ, gắn lên hồ sơ. */
public class BadgeCongratsFragment extends Fragment {

    private static final String ARG_BADGE = "badge_json";

    private FragmentBadgeCongratsBinding binding;
    private BadgeDto badge;

    public static Bundle args(BadgeDto badge) {
        Bundle b = new Bundle();
        b.putString(ARG_BADGE, App.container().gson.toJson(badge));
        return b;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBadgeCongratsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        String json = getArguments() != null ? getArguments().getString(ARG_BADGE) : null;
        badge = json != null ? App.container().gson.fromJson(json, BadgeDto.class) : null;
        if (badge == null) {
            nav.popBackStack();
            return;
        }
        binding.txtBadgeName.setText(badge.name);
        binding.txtBadgeDesc.setText(badge.description);
        binding.imgBadgeIcon.setImageResource(BadgeAdapter.iconOf(badge.icon));
        Date unlocked = CardDto.parse(badge.unlockedAt);
        binding.txtUnlockedAt.setText(getString(R.string.badge_unlocked_at, unlocked == null ? ""
                : new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(unlocked)));

        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnClose.setOnClickListener(v -> nav.popBackStack());
        binding.btnLater.setOnClickListener(v -> nav.popBackStack());
        binding.btnShare.setOnClickListener(v -> share());
        binding.btnFeature.setOnClickListener(v -> feature(nav));

        if (savedInstanceState == null) {
            playEntrance();
        }
    }

    /** Huy hiệu phóng to có nảy nhẹ cùng lúc pháo giấy bung ra. */
    private void playEntrance() {
        binding.badgeHero.setScaleX(0.3f);
        binding.badgeHero.setScaleY(0.3f);
        binding.badgeHero.setAlpha(0f);
        binding.badgeHero.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(80)
                .setDuration(450).setInterpolator(new OvershootInterpolator(1.6f)).start();
        binding.lottieConfetti.playAnimation();
    }

    /** Chia sẻ qua Intent ACTION_SEND (Messenger, Zalo, Gmail...). */
    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.badge_share_subject));
        send.putExtra(Intent.EXTRA_TEXT, getString(R.string.badge_share_text, badge.name, badge.description));
        startActivity(Intent.createChooser(send, getString(R.string.badge_share_chooser)));
    }

    private void feature(NavController nav) {
        binding.btnFeature.setEnabled(false);
        App.container().badgeRepository.feature(badge.code, r -> {
            if (binding == null) {
                return;
            }
            binding.btnFeature.setEnabled(true);
            if (r.isSuccess()) {
                Toast.makeText(requireContext(), getString(R.string.badge_featured, badge.name), Toast.LENGTH_SHORT).show();
                if (!nav.popBackStack(R.id.profile, false)) {
                    nav.navigate(R.id.profile);
                }
            } else {
                Toast.makeText(requireContext(), r.message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
