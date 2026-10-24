package com.example.nt118_project.ui.dictionary;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.nt118_project.R;
import com.example.nt118_project.ui.common.StateView;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.LookupDto;
import com.example.nt118_project.data.remote.dto.VocabularyDto;
import com.example.nt118_project.databinding.FragmentDictionarySearchBinding;

import java.util.Collections;
import java.util.List;

/** Tra từ tiếng Anh: gõ để tìm, chạm kết quả xem chi tiết, chip "Tìm gần đây" từ lịch sử. */
public class DictionarySearchFragment extends Fragment {

    private static final int MAX_RECENT = 8;

    private FragmentDictionarySearchBinding binding;
    private DictionaryViewModel viewModel;
    private DictionaryResultAdapter adapter;
    private StateView state;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDictionarySearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        NavController nav = NavHostFragment.findNavController(this);
        viewModel = new ViewModelProvider(this).get(DictionaryViewModel.class);
        state = new StateView(binding.state);

        adapter = new DictionaryResultAdapter(word -> nav.navigate(R.id.dictionary_detail,
                DictionaryDetailFragment.args(word)));
        binding.rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvResults.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> nav.popBackStack());
        binding.btnHistory.setOnClickListener(v -> {
            viewModel.loadHistory();
            binding.scroll.smoothScrollTo(0, 0);
        });
        binding.btnClearQuery.setOnClickListener(v -> binding.edtSearch.setText(""));
        binding.txtClearHistory.setOnClickListener(v -> viewModel.clearHistory());
        binding.edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.btnClearQuery.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                viewModel.onQueryChanged(s.toString());
            }
        });
        binding.edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.searchNow(v.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });

        viewModel.results().observe(getViewLifecycleOwner(), this::renderResults);
        viewModel.history().observe(getViewLifecycleOwner(), this::renderHistory);
        if (savedInstanceState == null) {
            viewModel.loadHistory();
            binding.edtSearch.requestFocus();
        }
    }

    private void renderResults(@Nullable Resource<List<VocabularyDto>> state) {
        boolean hasQuery = !viewModel.query().isEmpty();
        if (state == null || !hasQuery) {
            adapter.submitList(Collections.emptyList());
            binding.groupResultsHeader.setVisibility(View.GONE);
            this.state.empty(R.drawable.ic_search, getString(R.string.dict_empty_title),
                    getString(R.string.dict_hint_start), null, null);
            return;
        }
        if (state.isLoading()) {
            this.state.loading();
            return;
        }
        if (state.isError()) {
            adapter.submitList(Collections.emptyList());
            binding.groupResultsHeader.setVisibility(View.GONE);
            this.state.error(state.message, () -> viewModel.searchNow(viewModel.query()));
            return;
        }
        List<VocabularyDto> rows = state.data != null ? state.data : Collections.emptyList();
        adapter.submitList(rows);
        binding.groupResultsHeader.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        binding.txtResultCount.setText(getString(R.string.dict_result_count, rows.size()));
        if (rows.isEmpty()) {
            this.state.empty(R.drawable.ic_search, getString(R.string.dict_no_result_title),
                    getString(R.string.dict_no_result, viewModel.query()), getString(R.string.dict_clear_query),
                    () -> binding.edtSearch.setText(""));
        } else {
            this.state.hide();
        }
    }

    private void renderHistory(@Nullable Resource<List<LookupDto>> state) {
        binding.recentChips.removeAllViews();
        List<LookupDto> rows = state != null && state.isSuccess() && state.data != null ? state.data : Collections.emptyList();
        binding.groupRecent.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < Math.min(rows.size(), MAX_RECENT); i++) {
            String keyword = rows.get(i).keyword;
            TextView chip = (TextView) inflater.inflate(R.layout.item_recent_chip, binding.recentChips, false);
            chip.setText(keyword);
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_text_secondary));
            chip.setOnClickListener(v -> {
                binding.edtSearch.setText(keyword);
                binding.edtSearch.setSelection(keyword.length());
                viewModel.searchNow(keyword);
            });
            binding.recentChips.addView(chip);
        }
    }


    private void hideKeyboard() {
        InputMethodManager imm = ContextCompat.getSystemService(requireContext(), InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.edtSearch.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
