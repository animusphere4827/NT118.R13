package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.common.ServerTime;
import com.example.nt118_project.databinding.FragmentTv3HomeBinding;
import com.example.nt118_project.databinding.ItemHomeNewsBinding;
import com.example.nt118_project.databinding.ItemTv3EntryBinding;
import com.example.nt118_project.learning.LearningPathViewModel;
import com.example.nt118_project.learning.PathUnlockPolicy;
import com.example.nt118_project.learning.StudyApi;
import com.example.nt118_project.ui.common.BottomNavHelper;
import com.example.nt118_project.ui.notification.BellBadge;
import com.example.nt118_project.ui.profile.AvatarBinder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;

import static com.example.nt118_project.learning.StudyJson.number;
import static com.example.nt118_project.learning.StudyJson.rows;
import static com.example.nt118_project.learning.StudyJson.value;

/** Trang chủ (L1.3): bài đang học, streak, tiến độ lộ trình, bài báo mới (vuốt ngang) và lối vào các luồng học. */
public class HomeFragment extends Fragment {

    @Nullable
    private FragmentTv3HomeBinding binding;
    private LearningPathViewModel viewModel;
    @Nullable
    private StudyApi studyApi;
    private static final int NEWS_COUNT = 8;
    @Nullable
    private String userName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTv3HomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentTv3HomeBinding b = binding;
        BottomNavHelper.bind(view, NavHostFragment.findNavController(this), R.id.tabHome);
        b.btnBell.setOnClickListener(v -> LessonNavigator.navigate(this, R.id.notifications, null));
        BellBadge.attach(this, b.btnBell);
        // Avatar trên top bar (TV2): theo phiên đăng nhập, chạm thì đi như tab Hồ sơ
        App.container().session.currentUser().observe(getViewLifecycleOwner(), user -> {
            AvatarBinder.bind(b.avatarFrame, b.txtInitials, b.imgAvatar,
                    user == null ? null : user.fullName, user == null ? null : user.avatarUrl);
            userName = Greeting.shortName(user == null ? null : user.fullName);
            bindGreeting();
        });
        b.btnAvatar.setOnClickListener(v -> view.findViewById(R.id.tabProfile).performClick());
        b.searchBar.setOnClickListener(v -> LessonNavigator.navigate(this, R.id.dictionary_search, null));
        entry(b.entryPath, R.drawable.ic_book_open, R.string.tv3_entry_path, R.string.tv3_entry_path_hint, R.id.learn_path);
        entry(b.entryQuiz, R.drawable.ic_clipboard_check, R.string.tv3_entry_quiz, R.string.tv3_entry_quiz_hint, R.id.quiz_part5);
        entry(b.entryReading, R.drawable.ic_file_text, R.string.tv3_entry_reading, R.string.tv3_entry_reading_hint, R.id.reading_list);
        b.newsSeeAll.setOnClickListener(v -> LessonNavigator.navigate(this, R.id.reading_list, null));
        entry(b.entryExam, R.drawable.ic_clock, R.string.tv3_entry_exam, R.string.tv3_entry_exam_hint, R.id.exam_list);

        viewModel = new ViewModelProvider(requireActivity()).get(LearningPathViewModel.class);
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
    }

    private void entry(ItemTv3EntryBinding row, @DrawableRes int icon, @StringRes int title,
                       @StringRes int hint, @IdRes int destination) {
        row.entryIcon.setImageResource(icon);
        row.entryTitle.setText(title);
        row.entryHint.setText(hint);
        row.getRoot().setOnClickListener(v -> LessonNavigator.navigate(this, destination, null));
    }

    private void render(LearningPathViewModel.UiState ui) {
        FragmentTv3HomeBinding b = binding;
        if (b == null || !StateViews.render(ui, b.content, b.state, b.mockBadge, viewModel::load)) {
            return;
        }
        PathUnlockPolicy.Result progress = ui.progress;
        b.progressRing.setMax(progress.totalCount());
        b.progressRing.setProgress(progress.completedCount);
        b.progressCount.setText(getString(R.string.tv3_progress_count, progress.completedCount, progress.totalCount()));
        b.progressLabel.setText(getString(R.string.tv3_progress_label, progress.percent()));

        PathUnlockPolicy.Entry current = progress.current;
        if (current == null) {
            b.continueLabel.setVisibility(View.GONE);
            b.continueTitle.setText(R.string.tv3_finished_title);
            b.continueSubtitle.setText(R.string.tv3_finished_body);
            b.continueCard.setOnClickListener(v -> LessonNavigator.navigate(this, R.id.exam_list, null));
        } else {
            b.continueLabel.setVisibility(View.VISIBLE);
            b.continueTitle.setText(current.lesson.title);
            b.continueSubtitle.setText(getString(R.string.tv3_lesson_meta, current.unit.title, current.lesson.minutes));
            b.continueCard.setOnClickListener(v -> LessonNavigator.open(this, current));
        }
    }

    /** Lời chào theo giờ trên máy + một câu gợi ý cố định trong ngày. */
    private void bindGreeting() {
        FragmentTv3HomeBinding b = binding;
        if (b == null) return;
        Calendar now = Calendar.getInstance();
        Greeting.Period period = Greeting.period(now.get(Calendar.HOUR_OF_DAY));
        String hello = getString(period.title);
        b.txtGreeting.setText(userName == null ? hello : getString(R.string.greet_with_name, hello, userName));
        String[] hints = getResources().getStringArray(period.hints);
        b.txtGreetingHint.setText(hints[Greeting.hintIndex(now.get(Calendar.DAY_OF_YEAR), hints.length)]);
        b.txtGreetingHint.setVisibility(View.VISIBLE);
    }

    /** Chuỗi ngày học liên tiếp thật (GET /learn/analytics), cùng nguồn với trang Cá nhân. */
    private void loadStreak() {
        App.container().apiCalls.enqueue(study().analytics(), r -> {
            FragmentTv3HomeBinding b = binding;
            if (b == null) return;
            JsonElement streak = r.isSuccess() && r.data != null ? r.data.get("current_streak") : null;
            b.streakValue.setText(streak == null || streak.isJsonNull()
                    ? getString(R.string.home_streak_unknown) : String.valueOf(streak.getAsInt()));
        });
    }

    /** Vài bài báo RSS mới nhất, vuốt ngang; lỗi hoặc chưa có bài thì ẩn cả mục. */
    private void loadNews() {
        App.container().apiCalls.enqueue(study().news(1, NEWS_COUNT, null), r -> {
            FragmentTv3HomeBinding b = binding;
            if (b == null) return;
            JsonArray list = r.isSuccess() ? rows(r.data, "rows") : new JsonArray();
            b.newsRow.removeAllViews();
            for (JsonElement item : list) bindNews(b, item.getAsJsonObject());
            b.newsSection.setVisibility(list.size() == 0 ? View.GONE : View.VISIBLE);
        });
    }

    private void bindNews(FragmentTv3HomeBinding b, JsonObject row) {
        ItemHomeNewsBinding card = ItemHomeNewsBinding.inflate(getLayoutInflater(), b.newsRow, true);
        card.newsCategory.setText(NewsCategories.label(requireContext(), value(row, "category")).toUpperCase(Locale.ROOT));
        card.newsTitle.setText(value(row, "title"));
        Date published = ServerTime.parse(value(row, "published_at"));
        String date = published == null ? value(row, "source")
                : DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("vi", "VN")).format(published);
        card.newsMeta.setText(getString(R.string.home_news_meta, date, (int) number(row, "word_count")));
        long id = number(row, "id");
        card.getRoot().setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong(NewsArticleFragment.ARG_ID, id);
            LessonNavigator.navigate(this, R.id.news_article, args);
        });
    }

    private StudyApi study() {
        if (studyApi == null) studyApi = App.container().apiClient.create(StudyApi.class);
        return studyApi;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.load();
        loadStreak();
        loadNews();
        bindGreeting();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
