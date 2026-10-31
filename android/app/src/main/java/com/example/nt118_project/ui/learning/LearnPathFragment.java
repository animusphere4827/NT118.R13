package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.databinding.FragmentTv3LearnPathBinding;
import com.example.nt118_project.databinding.ItemTv3PathLessonBinding;
import com.example.nt118_project.databinding.ItemTv3PathUnitBinding;
import com.example.nt118_project.learning.LearningPathViewModel;
import com.example.nt118_project.learning.PathUnit;
import com.example.nt118_project.learning.PathUnlockPolicy;
import com.example.nt118_project.ui.common.BottomNavHelper;

/** Lộ trình (L1.3): các phần và bài, trạng thái đã xong / đang học / khóa. */
public class LearnPathFragment extends Fragment {

    @Nullable
    private FragmentTv3LearnPathBinding binding;
    private LearningPathViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTv3LearnPathBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        BottomNavHelper.bind(view, NavHostFragment.findNavController(this), R.id.tabLearn);
        viewModel = new ViewModelProvider(requireActivity()).get(LearningPathViewModel.class);
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(LearningPathViewModel.UiState ui) {
        FragmentTv3LearnPathBinding b = binding;
        if (b == null) {
            return;
        }
        if (!StateViews.render(ui, b.content, b.state, b.mockBadge, viewModel::load)) {
            b.pathSummary.setText("");
            return;
        }
        PathUnlockPolicy.Result progress = ui.progress;
        b.pathSummary.setText(getString(R.string.tv3_path_summary, progress.completedCount, progress.totalCount()));
        LayoutInflater inflater = getLayoutInflater();
        b.unitList.removeAllViews();
        for (PathUnit unit : ui.units) {
            ItemTv3PathUnitBinding unitRow = ItemTv3PathUnitBinding.inflate(inflater, b.unitList, false);
            unitRow.unitTitle.setText(unit.title);
            unitRow.unitDescription.setText(unit.description);
            for (PathUnlockPolicy.Entry entry : progress.entriesOf(unit)) {
                unitRow.lessonList.addView(lessonRow(inflater, unitRow, entry));
            }
            b.unitList.addView(unitRow.getRoot());
        }
    }

    private View lessonRow(LayoutInflater inflater, ItemTv3PathUnitBinding parent, PathUnlockPolicy.Entry entry) {
        ItemTv3PathLessonBinding row = ItemTv3PathLessonBinding.inflate(inflater, parent.lessonList, false);
        row.lessonTitle.setText(entry.lesson.title);
        row.lessonMeta.setText(getString(R.string.tv3_lesson_meta, entry.lesson.subtitle, entry.lesson.minutes));
        int badge;
        int icon;
        int iconTint;
        int label;
        int description;
        switch (entry.state) {
            case DONE:
                badge = R.drawable.bg_circle_success;
                icon = R.drawable.ic_check;
                iconTint = R.color.white;
                label = R.string.tv3_state_done;
                description = R.string.tv3_state_done_desc;
                break;
            case IN_PROGRESS:
                badge = R.drawable.bg_circle_primary;
                icon = R.drawable.ic_play;
                iconTint = R.color.white;
                label = R.string.tv3_state_current;
                description = R.string.tv3_state_current_desc;
                break;
            case LOCKED:
            default:
                badge = R.drawable.bg_circle_neutral;
                icon = R.drawable.ic_lock;
                iconTint = R.color.app_text_secondary;
                label = R.string.tv3_state_locked;
                description = R.string.tv3_state_locked_desc;
                break;
        }
        row.lessonBadge.setBackgroundResource(badge);
        row.lessonIcon.setImageResource(icon);
        row.lessonIcon.setColorFilter(ContextCompat.getColor(requireContext(), iconTint));
        row.lessonState.setText(label);
        boolean locked = entry.state == PathUnlockPolicy.State.LOCKED;
        row.getRoot().setAlpha(locked ? 0.6f : 1f);
        row.getRoot().setContentDescription(getString(description, entry.lesson.title));
        row.getRoot().setOnClickListener(v -> LessonNavigator.open(this, entry));
        return row.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
