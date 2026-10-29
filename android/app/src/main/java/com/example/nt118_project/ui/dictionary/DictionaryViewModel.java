package com.example.nt118_project.ui.dictionary;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.LookupDto;
import com.example.nt118_project.data.remote.dto.VocabularyDto;
import com.example.nt118_project.data.repository.DictionaryRepository;
import com.example.nt118_project.data.repository.ExternalDictionaryRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Màn tra từ: tìm khi gõ (trễ 300ms) và lịch sử tra gần đây. Kết quả gồm từ trong DB (có nghĩa tiếng Việt,
 * hiện trước) và gợi ý từ điển mở bên ngoài cho mọi từ tiếng Anh khác.
 */
public class DictionaryViewModel extends ViewModel {

    private static final long DEBOUNCE_MS = 300;

    private final DictionaryRepository repository = App.container().dictionaryRepository;
    private final ExternalDictionaryRepository external = App.container().externalDictionaryRepository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Resource<List<VocabularyDto>>> results = new MutableLiveData<>();
    private final MutableLiveData<Resource<List<LookupDto>>> history = new MutableLiveData<>();
    private String query = "";
    private Runnable pending;

    public LiveData<Resource<List<VocabularyDto>>> results() {
        return results;
    }

    public LiveData<Resource<List<LookupDto>>> history() {
        return history;
    }

    public String query() {
        return query;
    }

    /** Gọi mỗi khi ô tìm kiếm đổi; chỉ gửi request khi người dùng ngừng gõ. */
    public void onQueryChanged(String text) {
        String q = text.trim();
        if (q.equals(query) && results.getValue() != null) {
            return;
        }
        query = q;
        if (pending != null) {
            handler.removeCallbacks(pending);
        }
        if (q.isEmpty()) {
            results.setValue(null);
            return;
        }
        pending = () -> searchNow(q);
        handler.postDelayed(pending, DEBOUNCE_MS);
    }

    public void searchNow(String q) {
        if (q.trim().isEmpty()) {
            return;
        }
        String key = q.trim();
        query = key;
        results.setValue(Resource.loading());
        Merge merge = new Merge(key);
        repository.search(key, merge::local);
        external.suggest(key, merge::external);
    }

    /** Gộp hai nguồn, cập nhật danh sách ngay khi một nguồn trả về để không phải chờ nguồn chậm hơn. */
    private final class Merge {
        private final String key;
        @Nullable
        private Resource<List<VocabularyDto>> local;
        @Nullable
        private Resource<List<VocabularyDto>> outside;

        Merge(String key) {
            this.key = key;
        }

        void local(Resource<List<VocabularyDto>> r) {
            local = r;
            publish();
        }

        void external(Resource<List<VocabularyDto>> r) {
            outside = r;
            publish();
        }

        private void publish() {
            // Bỏ kết quả của truy vấn cũ
            if (!query.equals(key)) {
                return;
            }
            List<VocabularyDto> rows = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (local != null && local.isSuccess() && local.data != null) {
                for (VocabularyDto w : local.data) {
                    rows.add(w);
                    seen.add(w.word.toLowerCase(Locale.ROOT));
                }
            }
            if (outside != null && outside.isSuccess() && outside.data != null) {
                for (VocabularyDto w : outside.data) {
                    if (seen.add(w.word.toLowerCase(Locale.ROOT))) {
                        rows.add(w);
                    }
                }
            }
            boolean bothDone = local != null && outside != null;
            if (rows.isEmpty() && !bothDone) {
                return;
            }
            if (rows.isEmpty() && local.isError() && outside.isError()) {
                results.setValue(Resource.error(local.message, local.code));
                return;
            }
            results.setValue(Resource.success(rows));
        }
    }

    public void loadHistory() {
        repository.history(history::postValue);
    }

    public void clearHistory() {
        repository.clearHistory(r -> {
            if (r.isSuccess()) {
                loadHistory();
            }
        });
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacksAndMessages(null);
    }
}
