package com.example.nt118_project.ui.dictionary;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.VocabularyDto;
import com.example.nt118_project.data.repository.DictionaryRepository;
import com.example.nt118_project.data.repository.ExternalDictionaryRepository;

/** Chi tiết một từ: tải đủ trường từ catalog (hoặc từ điển mở nếu ngoài DB) và ghi lịch sử tra từ một lần. */
public class WordDetailViewModel extends ViewModel {

    private final DictionaryRepository repository = App.container().dictionaryRepository;
    private final ExternalDictionaryRepository external = App.container().externalDictionaryRepository;
    private final MutableLiveData<Resource<VocabularyDto>> detail = new MutableLiveData<>();
    private boolean started;

    public LiveData<Resource<VocabularyDto>> detail() {
        return detail;
    }

    public void start(long id, String word) {
        if (started) {
            return;
        }
        started = true;
        repository.recordLookup(word);
        load(id, word);
    }

    /** id = 0: từ ngoài DB, tra từ điển mở theo chữ. */
    public void load(long id, String word) {
        detail.setValue(Resource.loading());
        if (id <= 0) {
            external.lookup(word, detail::postValue);
        } else {
            repository.detail(id, detail::postValue);
        }
    }
}
