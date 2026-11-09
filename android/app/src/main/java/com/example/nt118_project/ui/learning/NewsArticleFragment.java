package com.example.nt118_project.ui.learning;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.common.ServerTime;
import com.example.nt118_project.common.Speaker;
import com.example.nt118_project.data.remote.dto.CardRequest;
import com.example.nt118_project.data.remote.dto.LookupRequest;
import com.example.nt118_project.data.remote.dto.VocabularyDto;
import com.example.nt118_project.databinding.FragmentNewsArticleBinding;
import com.example.nt118_project.learning.StudyApi;
import com.example.nt118_project.ui.dictionary.AddToDeckDialog;
import com.google.gson.JsonObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import retrofit2.Call;

import static com.example.nt118_project.learning.StudyJson.number;
import static com.example.nt118_project.learning.StudyJson.value;

/**
 * Đọc một bài báo tiếng Anh lấy từ RSS (tiêu đề + đoạn tóm tắt công khai của báo).
 * Chạm một từ để tra: thư viện từ vựng của nhóm trước, không có thì từ điển mở; lưu được vào bộ thẻ.
 * Nút "Đọc toàn bài" mở bài gốc trên trang của báo.
 */
public final class NewsArticleFragment extends Fragment {

    public static final String ARG_ID = "newsId";

    private FragmentNewsArticleBinding binding;
    private Speaker speaker;
    private Call<JsonObject> pending;
    private int lookupSeq;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNewsArticleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        speaker = new Speaker(requireContext());
        binding.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.btnCloseSheet.setOnClickListener(v -> binding.lookupSheet.setVisibility(View.GONE));
        binding.btnSpeak.setOnClickListener(v -> {
            speaker.warnIfUnavailable();
            speaker.speak(binding.txtWord.getText().toString());
        });
        binding.btnOpenSource.setVisibility(View.GONE);
        attachTapLookup(binding.txtSummary);
        long id = getArguments() == null ? 0 : getArguments().getLong(ARG_ID, 0);
        load(id);
    }

    private void load(long id) {
        binding.txtStatus.setText(R.string.loading);
        binding.txtStatus.setOnClickListener(null);
        pending = App.container().apiClient.create(StudyApi.class).newsArticle(id);
        Call<JsonObject> call = pending;
        App.container().apiCalls.enqueue(call, result -> {
            if (binding == null || call.isCanceled()) return;
            if (!result.isSuccess() || result.data == null) {
                binding.txtStatus.setText(result.message);
                binding.txtStatus.setOnClickListener(v -> load(id));
                return;
            }
            binding.txtStatus.setText("");
            show(result.data);
        });
    }

    private void show(JsonObject article) {
        String image = value(article, "image_url");
        binding.imgCover.setVisibility(image.isEmpty() ? View.GONE : View.VISIBLE);
        if (!image.isEmpty()) Glide.with(this).load(image).centerCrop().into(binding.imgCover);
        String credit = value(article, "image_credit");
        binding.txtCredit.setVisibility(credit.isEmpty() ? View.GONE : View.VISIBLE);
        binding.txtCredit.setText(credit);

        Date published = ServerTime.parse(value(article, "published_at"));
        String date = published == null ? "" : DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("vi", "VN")).format(published);
        binding.txtMeta.setText(getString(R.string.news_meta, NewsCategories.label(requireContext(), value(article, "category")),
                date, (int) number(article, "word_count")));
        binding.txtTitle.setText(value(article, "title"));
        binding.txtSummary.setText(value(article, "summary"));

        String source = value(article, "source");
        String url = value(article, "url");
        binding.txtAttribution.setText(getString(R.string.news_attribution, source));
        binding.btnOpenSource.setVisibility(url.isEmpty() ? View.GONE : View.VISIBLE);
        binding.btnOpenSource.setText(getString(R.string.news_open_source, source));
        binding.btnOpenSource.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(requireContext(), R.string.news_no_browser, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Chạm một lần vào từ để tra; nhấn giữ vẫn bôi đen chữ như bình thường. */
    @SuppressLint("ClickableViewAccessibility")
    private void attachTapLookup(TextView content) {
        GestureDetector detector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                if (content.hasSelection() || !insideText(content, e.getX(), e.getY())) return false;
                String word = ReadingWords.wordAt(content.getText(), content.getOffsetForPosition(e.getX(), e.getY()));
                if (word != null) lookup(word);
                return false;
            }
        });
        content.setOnTouchListener((v, e) -> {
            detector.onTouchEvent(e);
            return false;
        });
    }

    private static boolean insideText(TextView view, float x, float y) {
        Layout layout = view.getLayout();
        if (layout == null) return false;
        float localY = y - view.getTotalPaddingTop() + view.getScrollY();
        if (localY < 0 || localY > layout.getHeight()) return false;
        int line = layout.getLineForVertical((int) localY);
        float localX = x - view.getTotalPaddingLeft() + view.getScrollX();
        return localX >= layout.getLineLeft(line) && localX <= layout.getLineRight(line);
    }

    private void lookup(String word) {
        int seq = ++lookupSeq;
        binding.lookupSheet.setVisibility(View.VISIBLE);
        binding.txtWord.setText(word);
        binding.txtIpa.setText("");
        binding.txtMeaning.setText(R.string.reading_looking_up);
        binding.txtExample.setText("");
        binding.btnSaveCard.setVisibility(View.GONE);
        // Ghi lịch sử tra cứu nguồn READING; lỗi không ảnh hưởng người đọc
        App.container().apiCalls.enqueue(App.container().learnApi.addLookup(new LookupRequest(word, "READING")), r -> { });
        App.container().dictionaryRepository.search(word, result -> {
            if (binding == null || seq != lookupSeq) return;
            VocabularyDto exact = null;
            if (result.isSuccess() && result.data != null) {
                for (VocabularyDto row : result.data) {
                    if (row.word != null && row.word.equalsIgnoreCase(word.replace('’', '\''))) {
                        exact = row;
                        break;
                    }
                }
            }
            if (exact != null) showLibraryWord(exact);
            else lookupExternal(word, seq);
        });
    }

    private void showLibraryWord(VocabularyDto entry) {
        binding.txtWord.setText(entry.word);
        binding.txtIpa.setText(entry.ipa == null ? "" : entry.ipa);
        String pos = entry.partOfSpeech == null ? "" : entry.partOfSpeech;
        binding.txtMeaning.setText(pos.isEmpty() ? entry.meaningVi : getString(R.string.reading_meaning, pos, entry.meaningVi));
        binding.txtExample.setText("");
        binding.btnSaveCard.setVisibility(View.VISIBLE);
        binding.btnSaveCard.setOnClickListener(v -> AddToDeckDialog.show(this, entry.id));
    }

    private void lookupExternal(String word, int seq) {
        App.container().externalDictionaryRepository.lookup(word, result -> {
            if (binding == null || seq != lookupSeq) return;
            VocabularyDto entry = result.data;
            String meaning = entry == null ? null : entry.meaningVi != null ? entry.meaningVi : entry.definitionEn;
            if (!result.isSuccess() || entry == null || meaning == null || meaning.trim().isEmpty()) {
                binding.txtMeaning.setText(R.string.reading_not_found);
                return;
            }
            binding.txtWord.setText(entry.word);
            binding.txtIpa.setText(entry.ipa == null ? "" : entry.ipa);
            String text = entry.partOfSpeech == null || entry.partOfSpeech.isEmpty() ? meaning
                    : getString(R.string.reading_meaning, entry.partOfSpeech, meaning);
            binding.txtMeaning.setText(getString(R.string.reading_external_meaning, text));
            binding.txtExample.setText(entry.exampleSentence == null ? "" : entry.exampleSentence);
            binding.btnSaveCard.setVisibility(View.VISIBLE);
            binding.btnSaveCard.setOnClickListener(v -> AddToDeckDialog.show(this, CardRequest.fromExternal(entry)));
        });
    }

    @Override
    public void onDestroyView() {
        if (pending != null) pending.cancel();
        pending = null;
        speaker.shutdown();
        binding = null;
        super.onDestroyView();
    }
}
