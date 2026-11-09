package com.example.nt118_project.ui.learning;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Layout;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.common.Speaker;
import com.example.nt118_project.data.remote.dto.CardRequest;
import com.example.nt118_project.data.remote.dto.VocabularyDto;
import com.example.nt118_project.ui.dictionary.AddToDeckDialog;
import com.example.nt118_project.databinding.FragmentReadingArticleBinding;
import com.example.nt118_project.databinding.ItemReadingPassageBinding;
import com.example.nt118_project.learning.StudyApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;

import static com.example.nt118_project.learning.StudyJson.body;
import static com.example.nt118_project.learning.StudyJson.number;
import static com.example.nt118_project.learning.StudyJson.rows;
import static com.example.nt118_project.learning.StudyJson.value;

/** Đọc một bài Reading: chạm hoặc chọn từ để tra nghĩa, lưu vào bộ thẻ, đánh dấu đã đọc xong. */
public final class ReadingStudyFragment extends Fragment {

    private static final int MENU_LOOKUP = 8241;

    private FragmentReadingArticleBinding binding;
    private StudyApi api;
    private Speaker speaker;
    private AlertDialog dialog;
    private final List<Call<?>> calls = new ArrayList<>();
    private final List<ItemReadingPassageBinding> passages = new ArrayList<>();
    private long id;
    private boolean translation;
    private boolean busy;
    /** Tăng mỗi lần tra; kết quả của lần tra cũ (chạm từ khác khi đang tải) bị bỏ qua. */
    private int lookupSeq;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReadingArticleBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        api = App.container().apiClient.create(StudyApi.class);
        speaker = new Speaker(requireContext());
        id = getArguments() == null ? 0 : getArguments().getLong("contentId", 0);
        translation = false;
        binding.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.btnTranslate.setOnClickListener(v -> toggleTranslation());
        binding.btnTranslateBottom.setOnClickListener(v -> toggleTranslation());
        binding.btnCloseSheet.setOnClickListener(v -> binding.lookupSheet.setVisibility(View.GONE));
        binding.btnSpeak.setOnClickListener(v -> {
            speaker.warnIfUnavailable();
            speaker.speak(binding.txtWord.getText().toString());
        });
        binding.txtTitle.setText("");
        binding.txtBadge.setText("");
        binding.txtMeta.setText("");
        load();
    }

    private <T> void request(Call<T> call, Done<T> success) {
        busy = true;
        calls.add(call);
        App.container().apiCalls.enqueue(call, result -> {
            calls.remove(call);
            if (binding == null || call.isCanceled()) return;
            busy = false;
            if (result.isSuccess()) {
                binding.txtStatus.setText("");
                success.accept(result.data);
            } else {
                binding.txtStatus.setText(result.message);
            }
        });
    }

    private interface Done<T> {
        void accept(T value);
    }

    private void load() {
        if (id <= 0) {
            binding.txtStatus.setText(R.string.reading_pick);
            binding.btnDone.setVisibility(View.GONE);
            binding.btnTranslateBottom.setVisibility(View.GONE);
            return;
        }
        // Bỏ bản dịch: chỉ đọc tiếng Anh, chạm từ để tra nghĩa
        request(api.reading(id, false), this::show);
    }

    private void show(JsonObject data) {
        binding.txtTitle.setText(value(data, "title"));
        JsonArray types = rows(data, "passage_types");
        String first = types.size() > 0 ? types.get(0).getAsString() : "OTHER";
        binding.txtBadge.setText(getString(R.string.reading_badge,
                PassageTypes.label(first).toUpperCase(Locale.ROOT), (int) number(data, "part_id")));
        binding.txtMeta.setText(getString(R.string.reading_passages, (int) number(data, "passage_count")));

        binding.passageList.removeAllViews();
        passages.clear();
        for (JsonElement item : rows(data, "passages")) {
            JsonObject passage = item.getAsJsonObject();
            ItemReadingPassageBinding card = ItemReadingPassageBinding.inflate(getLayoutInflater(), binding.passageList, true);
            String title = value(passage, "title");
            String type = PassageTypes.label(value(passage, "passage_type"));
            card.txtHeading.setText(title.isEmpty() ? type : type + " · " + title);
            String image = value(passage, "image_url");
            card.imgPassage.setVisibility(image.isEmpty() ? View.GONE : View.VISIBLE);
            if (!image.isEmpty()) Glide.with(this).load(image).fitCenter().into(card.imgPassage);
            card.txtContent.setText(value(passage, "content"));
            card.txtContent.setCustomSelectionActionModeCallback(lookupMenu(card.txtContent));
            attachTapLookup(card.txtContent);
            card.txtTranslation.setText(value(passage, "translation_vi"));
            card.txtTranslation.setTag(!value(passage, "translation_vi").isEmpty());
            passages.add(card);
        }
        applyTranslation();

        boolean done = !value(data, "completed_at").isEmpty();
        binding.btnDone.setText(done ? R.string.reading_completed : R.string.reading_mark_done);
        binding.btnDone.setEnabled(!done);
        binding.btnDone.setAlpha(done ? 0.6f : 1f);
        binding.btnDone.setOnClickListener(done ? null : v -> {
            if (busy) return;
            JsonObject body = new JsonObject();
            body.addProperty("completed", true);
            request(api.completeReading(id, body), ignored -> load());
        });
    }

    private void toggleTranslation() {
        translation = !translation;
        applyTranslation();
    }

    private void applyTranslation() {
        for (ItemReadingPassageBinding card : passages) {
            boolean has = Boolean.TRUE.equals(card.txtTranslation.getTag());
            card.txtTranslation.setVisibility(translation && has ? View.VISIBLE : View.GONE);
        }
        binding.txtTranslateBottom.setText(translation ? R.string.reading_hide_translation : R.string.reading_show_translation);
    }

    private ActionMode.Callback lookupMenu(TextView content) {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0, MENU_LOOKUP, 0, R.string.reading_lookup);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() != MENU_LOOKUP) return false;
                int start = content.getSelectionStart();
                int end = content.getSelectionEnd();
                if (start >= 0 && end > start) lookup(content.getText().subSequence(start, end).toString().trim());
                mode.finish();
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        };
    }

    /**
     * Chạm một lần vào từ bất kỳ trong đoạn văn để tra; nhấn giữ vẫn bôi đen và dùng menu "Tra từ" như cũ.
     * Trả false để TextView vẫn xử lý chọn chữ.
     */
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

    /** Bỏ qua chạm vào khoảng trống bên phải dòng hoặc dưới đoạn văn. */
    private static boolean insideText(TextView view, float x, float y) {
        Layout layout = view.getLayout();
        if (layout == null) return false;
        float localY = y - view.getTotalPaddingTop() + view.getScrollY();
        if (localY < 0 || localY > layout.getHeight()) return false;
        int line = layout.getLineForVertical((int) localY);
        float localX = x - view.getTotalPaddingLeft() + view.getScrollX();
        return localX >= layout.getLineLeft(line) && localX <= layout.getLineRight(line);
    }

    /**
     * Tra trong thư viện từ vựng của nhóm trước (server ghi luôn lịch sử tra cứu, có nút lưu theo vocabulary_id).
     * Không có trong thư viện thì tra từ điển mở (Free Dictionary + MyMemory) và lưu thành thẻ tự nhập.
     */
    private void lookup(String raw) {
        String word = raw.trim();
        if (word.isEmpty() || binding == null) return;
        int seq = ++lookupSeq;
        binding.lookupSheet.setVisibility(View.VISIBLE);
        binding.txtWord.setText(word);
        binding.txtIpa.setText("");
        binding.txtMeaning.setText(R.string.reading_looking_up);
        binding.txtExample.setText("");
        binding.btnSaveCard.setVisibility(View.GONE);
        Call<JsonObject> call = api.lookup(id, body("keyword", word));
        calls.add(call);
        App.container().apiCalls.enqueue(call, result -> {
            calls.remove(call);
            if (binding == null || call.isCanceled() || seq != lookupSeq) return;
            JsonArray found = result.isSuccess() && result.data != null ? rows(result.data, "rows") : new JsonArray();
            if (found.size() == 0) {
                lookupExternal(word, seq);
                return;
            }
            JsonObject entry = found.get(0).getAsJsonObject();
            binding.txtWord.setText(value(entry, "word"));
            binding.txtIpa.setText(value(entry, "ipa"));
            String pos = value(entry, "part_of_speech");
            binding.txtMeaning.setText(pos.isEmpty() ? value(entry, "meaning_vi")
                    : getString(R.string.reading_meaning, pos, value(entry, "meaning_vi")));
            binding.txtExample.setText(value(entry, "example_sentence"));
            binding.btnSaveCard.setVisibility(View.VISIBLE);
            binding.btnSaveCard.setOnClickListener(v -> chooseDeck(number(entry, "id")));
        });
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

    private void chooseDeck(long vocabularyId) {
        if (busy) return;
        request(api.decks(), data -> {
            JsonArray decks = rows(data, "rows");
            String[] names = new String[decks.size() + 1];
            for (int i = 0; i < decks.size(); i++) names[i] = value(decks.get(i).getAsJsonObject(), "name");
            names[decks.size()] = getString(R.string.reading_new_deck);
            dismissDialog();
            dialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.reading_choose_deck)
                    .setItems(names, (d, index) -> {
                        if (index == decks.size()) newDeck(vocabularyId);
                        else saveWord(number(decks.get(index).getAsJsonObject(), "id"), vocabularyId);
                    })
                    .setNegativeButton(R.string.reading_cancel, null)
                    .show();
        });
    }

    private void newDeck(long vocabularyId) {
        EditText name = new EditText(requireContext());
        name.setHint(R.string.reading_deck_name);
        dismissDialog();
        dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.reading_new_deck)
                .setView(name)
                .setPositiveButton(R.string.reading_create_and_save, (d, w) -> {
                    JsonObject body = body("name", name.getText().toString().trim());
                    body.addProperty("description", "");
                    body.add("cover_image_url", JsonNull.INSTANCE);
                    request(api.deck(body), data -> saveWord(number(data, "id"), vocabularyId));
                })
                .setNegativeButton(R.string.reading_cancel, null)
                .show();
    }

    private void saveWord(long deck, long vocabulary) {
        JsonObject body = new JsonObject();
        body.addProperty("vocabulary_id", vocabulary);
        request(api.card(deck, body), data -> {
            binding.lookupSheet.setVisibility(View.GONE);
            Toast.makeText(requireContext(), R.string.reading_saved, Toast.LENGTH_SHORT).show();
        });
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        out.putBoolean("translation", translation);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onDestroyView() {
        for (Call<?> call : calls) call.cancel();
        calls.clear();
        passages.clear();
        dismissDialog();
        speaker.shutdown();
        binding = null;
        super.onDestroyView();
    }
}
