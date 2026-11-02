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

import java.util.Locale;

import retrofit2.Call;

import static com.example.nt118_project.learning.StudyJson.number;
import static com.example.nt118_project.learning.StudyJson.rows;
import static com.example.nt118_project.learning.StudyJson.value;

/** Thư viện bài đọc thực tế, lọc theo loại văn bản, tải thêm theo trang. */
public final class ReadingListFragment extends Fragment {

    private static final int PAGE_SIZE = 20;

    private FragmentReadingListBinding binding;
    private StudyApi api;
    private Call<JsonObject> pending;
    @Nullable
    private String type;
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
        binding.btnBack.setOnClickListener(v -> nav().popBackStack());
        binding.btnSearch.setOnClickListener(v -> nav().navigate(R.id.dictionary_search));
        binding.btnMore.setOnClickListener(v -> load(page + 1));
        bindChips();
        load(1);
    }

    private NavController nav() {
        return NavHostFragment.findNavController(this);
    }

    private void bindChips() {
        binding.chipRow.removeAllViews();
        addChip(getString(R.string.reading_all), null);
        for (String value : PassageTypes.FILTERS) addChip(PassageTypes.label(value), value);
    }

    private void addChip(String text, @Nullable String value) {
        TextView chip = new TextView(requireContext(), null, 0, R.style.Chip);
        chip.setText(text);
        boolean selected = value == null ? type == null : value.equals(type);
        chip.setSelected(selected);
        chip.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.app_primary : R.color.app_text_secondary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Math.round(8 * getResources().getDisplayMetrics().density));
        chip.setOnClickListener(v -> {
            if (selected) return;
            type = value;
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
        Call<JsonObject> call = api.readings(target, PAGE_SIZE, "", type);
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
            for (JsonElement item : list) bindCard(item.getAsJsonObject());
            long total = number(result.data, "total");
            binding.txtStatus.setText(total == 0 ? getString(R.string.list_empty) : "");
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        out.putString("type", type);
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
