package com.example.nt118_project.ui.dictionary;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.LookupDto;
import com.example.nt118_project.data.remote.dto.VocabularyDto;
import com.example.nt118_project.data.repository.DictionaryRepository;

import java.util.List;

/** Màn tra từ: tìm khi gõ (trễ 300ms) và lịch sử tra gần đây. */
public class DictionaryViewModel extends ViewModel {

    private static final long DEBOUNCE_MS = 300;

    private final DictionaryRepository repository = App.container().dictionaryRepository;
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
        query = q.trim();
        results.setValue(Resource.loading());
        repository.search(query, r -> {
            // Bỏ kết quả của truy vấn cũ
            if (query.equals(q.trim())) {
                results.postValue(r);
            }
        });
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
