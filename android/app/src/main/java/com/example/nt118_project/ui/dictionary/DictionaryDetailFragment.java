package com.example.nt118_project.ui.dictionary;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Speaker;
import com.example.nt118_project.data.remote.dto.VocabularyDto;
import com.example.nt118_project.databinding.FragmentDictionaryDetailBinding;

import java.util.Locale;

/** Chi tiết từ: nghĩa, ví dụ, phát âm TextToSpeech, thêm vào flashcard. */
public class DictionaryDetailFragment extends Fragment {

    private static final String ARG_ID = "vocabulary_id";
    private static final String ARG_WORD = "word";
    private static final String ARG_IPA = "ipa";
    private static final String ARG_MEANING = "meaning";
    private static final String ARG_POS = "pos";
    private static final String ARG_TOPIC = "topic";

    private FragmentDictionaryDetailBinding binding;
    private Speaker speaker;
    private long vocabularyId;

    /** Truyền dữ liệu có sẵn từ kết quả tìm để hiển thị ngay trong lúc tải chi tiết. */
    public static Bundle args(VocabularyDto w) {
        Bundle b = new Bundle();
        b.putLong(ARG_ID, w.id);
        b.putString(ARG_WORD, w.word);
        b.putString(ARG_IPA, w.ipa);
        b.putString(ARG_MEANING, w.meaningVi);
        b.putString(ARG_POS, w.partOfSpeech);
        b.putString(ARG_TOPIC, w.topic);
        return b;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        speaker = new Speaker(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDictionaryDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        vocabularyId = args.getLong(ARG_ID);
        VocabularyDto preview = new VocabularyDto();
        preview.id = vocabularyId;
        preview.word = args.getString(ARG_WORD, "");
        preview.ipa = args.getString(ARG_IPA);
        preview.meaningVi = args.getString(ARG_MEANING);
        preview.partOfSpeech = args.getString(ARG_POS);
        preview.topic = args.getString(ARG_TOPIC);
        bind(preview, preview.topic);

        binding.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.btnSpeak.setOnClickListener(v -> {
            speaker.warnIfUnavailable();
            speaker.speak(binding.txtWord.getText().toString());
        });
        // Chạm câu ví dụ tiếng Anh để nghe
        binding.txtExample.setOnClickListener(v -> speaker.speak(binding.txtExample.getText().toString()));
        binding.btnAddFlashcard.setOnClickListener(v -> AddToDeckDialog.show(this, vocabularyId));
        binding.btnBookmark.setOnClickListener(v -> AddToDeckDialog.show(this, vocabularyId));

        WordDetailViewModel viewModel = new ViewModelProvider(this).get(WordDetailViewModel.class);
        viewModel.detail().observe(getViewLifecycleOwner(), state -> {
            if (state == null || state.isLoading()) {
                return;
            }
            if (state.isSuccess() && state.data != null) {
                bind(state.data, preview.topic);
            } else {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.start(vocabularyId, preview.word);
    }

    private void bind(VocabularyDto w, @Nullable String topic) {
        binding.txtWord.setText(w.word);
        setOrHide(binding.txtTopic, empty(topic) ? null
                : getString(R.string.dict_topic_format, topic.toUpperCase(Locale.ROOT)));
        setOrHide(binding.txtIpa, w.ipa);
        setOrHide(binding.txtPos, w.partOfSpeech);
        setOrHide(binding.txtMeaning, w.meaningVi);
        setOrHide(binding.txtExample, w.exampleSentence);
        setOrHide(binding.txtExampleVi, w.exampleVi);

        // Khối thứ hai của thẻ: định nghĩa tiếng Anh nếu có
        boolean hasDefinition = !empty(w.definitionEn);
        binding.divider2.setVisibility(hasDefinition ? View.VISIBLE : View.GONE);
        setOrHide(binding.txtMeaning2, hasDefinition ? getString(R.string.dict_definition_en) : null);
        setOrHide(binding.txtExample2, w.definitionEn);
        binding.txtExample2Vi.setVisibility(View.GONE);

        StringBuilder family = new StringBuilder();
        if (!empty(w.synonyms)) {
            family.append(getString(R.string.dict_synonyms, w.synonyms));
        }
        if (!empty(w.antonyms)) {
            if (family.length() > 0) {
                family.append('\n');
            }
            family.append(getString(R.string.dict_antonyms, w.antonyms));
        }
        binding.txtFamilyLabel.setText(R.string.dict_family_label);
        binding.txtFamilyLabel.setVisibility(family.length() == 0 ? View.GONE : View.VISIBLE);
        setOrHide(binding.txtFamily, family.length() == 0 ? null : family.toString());
    }

    private static void setOrHide(TextView view, @Nullable String text) {
        view.setVisibility(empty(text) ? View.GONE : View.VISIBLE);
        view.setText(text);
    }

    private static boolean empty(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        speaker.stop();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        speaker.shutdown();
    }
}
