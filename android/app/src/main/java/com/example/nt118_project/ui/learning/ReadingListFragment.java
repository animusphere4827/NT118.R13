package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.databinding.FragmentReadingListBinding;
import com.example.nt118_project.databinding.ItemReadingCardBinding;
import com.example.nt118_project.learning.StudyApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.example.nt118_project.common.ServerTime;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;

import static com.example.nt118_project.learning.StudyJson.number;
import static com.example.nt118_project.learning.StudyJson.rows;
import static com.example.nt118_project.learning.StudyJson.value;

/**
 * Thư viện Reading có 2 tab: "Bài báo" (tin tiếng Anh lấy từ RSS, lọc theo chuyên mục) và
 * "Luyện TOEIC" (bài đọc Part 6/7, lọc theo loại văn bản). Tải thêm theo trang.
 */
public final class ReadingListFragment extends Fragment {

    private static final int PAGE_SIZE = 20;

    private FragmentReadingListBinding binding;
    private StudyApi api;
    private Call<JsonObject> pending;
    @Nullable
    private String type;
    @Nullable
    private String category;
    /** true: tab Bài báo (mặc định), false: tab Luyện TOEIC. */
    private boolean news = true;
    private int page;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReadingListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        api = App.container().apiClient.create(StudyApi.class);
        type = saved == null ? null : saved.getString("type");
        category = saved == null ? null : saved.getString("category");
        news = saved == null || saved.getBoolean("news", true);
        binding.tabNews.setOnClickListener(v -> switchTab(true));
        binding.tabToeic.setOnClickListener(v -> switchTab(false));
        bindTabs();
        binding.btnBack.setOnClickListener(v -> nav().popBackStack());
        binding.btnSearch.setOnClickListener(v -> nav().navigate(R.id.dictionary_search));
        binding.btnMore.setOnClickListener(v -> load(page + 1));
        bindChips();
        load(1);
    }

    private NavController nav() {
        return NavHostFragment.findNavController(this);
    }

    private void switchTab(boolean toNews) {
        if (news == toNews) return;
        news = toNews;
        bindTabs();
        bindChips();
        load(1);
    }

    private void bindTabs() {
        styleTab(binding.tabNews, news);
        styleTab(binding.tabToeic, !news);
        binding.txtSubtitle.setText(news ? getString(R.string.news_list_hint, "The Guardian") : getString(R.string.reading_list_05));
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setSelected(selected);
        tab.setBackgroundResource(selected ? R.drawable.bg_logo : 0);
        tab.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.white : R.color.app_text_secondary));
    }

    private void bindChips() {
        binding.chipRow.removeAllViews();
        addChip(getString(R.string.reading_all), null);
        if (news) for (String value : NewsCategories.FILTERS) addChip(NewsCategories.label(requireContext(), value), value);
        else for (String value : PassageTypes.FILTERS) addChip(PassageTypes.label(value), value);
    }

    private void addChip(String text, @Nullable String value) {
        TextView chip = new TextView(requireContext(), null, 0, R.style.Chip);
        chip.setText(text);
        String current = news ? category : type;
        boolean selected = value == null ? current == null : value.equals(current);
        chip.setSelected(selected);
        chip.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.app_primary : R.color.app_text_secondary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Math.round(8 * getResources().getDisplayMetrics().density));
        chip.setOnClickListener(v -> {
            if (selected) return;
            if (news) category = value;
            else type = value;
            bindChips();
            load(1);
        });
        binding.chipRow.addView(chip, params);
    }

    private void load(int target) {
        if (pending != null) pending.cancel();
        if (target == 1) binding.readingList.removeAllViews();
        binding.btnMore.setVisibility(View.GONE);
        binding.txtStatus.setText("");
        binding.txtStatus.setOnClickListener(null);
        Call<JsonObject> call = news ? api.news(target, PAGE_SIZE, category) : api.readings(target, PAGE_SIZE, "", type);
        boolean newsPage = news;
        pending = call;
        App.container().apiCalls.enqueue(call, result -> {
            if (binding == null || call.isCanceled()) return;
            pending = null;
            if (!result.isSuccess()) {
                binding.txtStatus.setText(result.message);
                binding.txtStatus.setOnClickListener(v -> load(target));
                return;
            }
            page = target;
            JsonArray list = rows(result.data, "rows");
            for (JsonElement item : list) {
                if (newsPage) bindNewsCard(item.getAsJsonObject());
                else bindCard(item.getAsJsonObject());
            }
            long total = number(result.data, "total");
            binding.txtStatus.setText(total == 0 ? getString(newsPage ? R.string.news_empty : R.string.list_empty) : "");
            binding.btnMore.setVisibility((long) page * PAGE_SIZE < total ? View.VISIBLE : View.GONE);
        });
    }

    private void bindCard(JsonObject row) {
        ItemReadingCardBinding card = ItemReadingCardBinding.inflate(getLayoutInflater(), binding.readingList, true);
        JsonArray types = rows(row, "passage_types");
        String first = types.size() > 0 ? types.get(0).getAsString() : "";
        card.txtType.setText(first.isEmpty() || first.equals("OTHER")
                ? getString(R.string.reading_default_type) : PassageTypes.label(first).toUpperCase(Locale.ROOT));
        boolean done = !value(row, "completed_at").isEmpty();
        card.imgDone.setVisibility(done ? View.VISIBLE : View.GONE);
        card.txtNew.setVisibility(done ? View.GONE : View.VISIBLE);
        card.txtTitle.setText(value(row, "title"));
        card.txtMeta.setText(getString(R.string.reading_meta, (int) number(row, "part_id"), (int) number(row, "passage_count")));
        long id = number(row, "id");
        card.getRoot().setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong("contentId", id);
            nav().navigate(R.id.reading_article, args);
        });
    }

    private void bindNewsCard(JsonObject row) {
        ItemReadingCardBinding card = ItemReadingCardBinding.inflate(getLayoutInflater(), binding.readingList, true);
        card.txtType.setText(NewsCategories.label(requireContext(), value(row, "category")).toUpperCase(Locale.ROOT));
        card.imgDone.setVisibility(View.GONE);
        card.txtNew.setVisibility(View.GONE);
        card.txtTitle.setText(value(row, "title"));
        Date published = ServerTime.parse(value(row, "published_at"));
        String date = published == null ? "" : DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("vi", "VN")).format(published);
        card.txtMeta.setText(getString(R.string.news_card_meta, value(row, "source"), date, (int) number(row, "word_count")));
        long id = number(row, "id");
        card.getRoot().setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong(NewsArticleFragment.ARG_ID, id);
            nav().navigate(R.id.news_article, args);
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        out.putString("type", type);
        out.putString("category", category);
        out.putBoolean("news", news);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onDestroyView() {
        if (pending != null) pending.cancel();
        pending = null;
        binding = null;
        super.onDestroyView();
    }
}
