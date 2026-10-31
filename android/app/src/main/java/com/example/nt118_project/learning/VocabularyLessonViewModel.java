package com.example.nt118_project.learning;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;

public final class VocabularyLessonViewModel extends ViewModel {
    public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public VocabularySession session;
    public VocabularySession.Check check;
    public VocabularySession.Result result;
    public VocabularySession.WordReview review;
    public int questionIndex;
    public boolean busy;
    public String error;
    private long lessonId;
    private Call<?> pending;
    private final Map<Long, String> answers = new HashMap<>();
    private final CourseApi api = App.container().apiClient.create(CourseApi.class);

    public void start(long id) {
        if (lessonId == id && (session != null || busy)) return;
        lessonId = id;
        refresh();
    }

    public void refresh() {
        if (busy) return;
        if (lessonId <= 0) { error = "Không tìm thấy bài học"; changed(); return; }
        begin();
        Call<VocabularySession> call = api.start(lessonId, Collections.emptyMap());
        pending = call;
        App.container().apiCalls.enqueue(call, response -> {
            if (call.isCanceled()) return;
            busy = false;
            if (response.isSuccess() && response.data != null) {
                session = response.data;
                check = session.check;
                questionIndex = 0;
                answers.clear();
                result = null;
                review = null;
            } else error = response.message;
            changed();
        });
    }

    public VocabularySession.Word nextWord() {
        if (session == null || session.words == null) return null;
        for (VocabularySession.Word word : session.words) {
            if (session.studied_ids == null || !session.studied_ids.contains(word.id)) return word;
        }
        return null;
    }

    public void study() {
        VocabularySession.Word word = nextWord();
        if (busy || word == null) return;
        begin();
        Call<VocabularySession> call = api.study(lessonId,
                Collections.singletonMap("vocabulary_ids", Collections.singletonList(word.id)));
        pending = call;
        App.container().apiCalls.enqueue(call, response -> {
            if (call.isCanceled()) return;
            busy = false;
            if (response.isSuccess() && response.data != null) session = response.data;
            else error = response.message;
            changed();
        });
    }

    public void beginCheck() {
        if (busy) return;
        begin();
        Call<VocabularySession.Check> call = api.check(lessonId, Collections.emptyMap());
        pending = call;
        App.container().apiCalls.enqueue(call, response -> {
            if (call.isCanceled()) return;
            busy = false;
            if (response.isSuccess() && response.data != null) {
                check = response.data;
                questionIndex = 0;
                answers.clear();
            } else error = response.message;
            changed();
        });
    }

    public String currentAnswer() {
        if (check == null) return "";
        return answers.getOrDefault(check.questions.get(questionIndex).vocabulary_id, "");
    }

    public void rememberAnswer(String value) {
        if (check != null && !busy && result == null) {
            answers.put(check.questions.get(questionIndex).vocabulary_id, value);
        }
    }

    public void previousQuestion() {
        if (!busy && questionIndex > 0) { questionIndex--; error = null; changed(); }
    }

    public void answer(String value) {
        answer(value, false);
    }

    public void skip() {
        answer("", true);
    }

    private void answer(String value, boolean allowBlank) {
        if (busy || check == null) return;
        if (!allowBlank && value.trim().isEmpty()) { error = "Nhập từ tiếng Anh hoặc chọn Chưa nhớ"; changed(); return; }
        rememberAnswer(value);
        if (questionIndex + 1 < check.questions.size()) { questionIndex++; error = null; changed(); return; }
        List<VocabularySession.Answer> submitted = new ArrayList<>();
        for (VocabularySession.Question question : check.questions) {
            submitted.add(new VocabularySession.Answer(question.vocabulary_id,
                    answers.getOrDefault(question.vocabulary_id, "")));
        }
        begin();
        Call<VocabularySession.Result> call = api.submit(check.id, Collections.singletonMap("answers", submitted));
        pending = call;
        App.container().apiCalls.enqueue(call, response -> {
            if (call.isCanceled()) return;
            if (!response.isSuccess() && (response.code == 0 || response.code == 409)) {
                recoverCheck(response.message);
                return;
            }
            busy = false;
            if (response.isSuccess() && response.data != null) result = response.data;
            else error = response.message;
            changed();
        });
    }

    private void recoverCheck(String originalError) {
        Call<VocabularySession.Check> call = api.savedCheck(check.id);
        pending = call;
        App.container().apiCalls.enqueue(call, response -> {
            if (call.isCanceled()) return;
            busy = false;
            if (response.isSuccess() && response.data != null && response.data.result != null) {
                result = response.data.result;
            } else error = originalError;
            changed();
        });
    }

    public void review(int page) {
        if (busy) return;
        begin();
        Call<VocabularySession.WordReview> call = api.review(lessonId, page);
        pending = call;
        App.container().apiCalls.enqueue(call, response -> {
            if (call.isCanceled()) return;
            busy = false;
            if (response.isSuccess() && response.data != null) review = response.data;
            else error = response.message;
            changed();
        });
    }

    private void begin() {
        busy = true;
        error = null;
        changed();
    }

    private void changed() {
        changes.setValue(changes.getValue() == null ? 1 : changes.getValue() + 1);
    }

    @Override
    protected void onCleared() {
        if (pending != null) pending.cancel();
    }
}
