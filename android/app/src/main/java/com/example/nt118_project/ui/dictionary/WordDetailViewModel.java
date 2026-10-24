package com.example.nt118_project.ui.dictionary;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.VocabularyDto;
import com.example.nt118_project.data.repository.DictionaryRepository;

/** Chi tiết một từ: tải đủ trường từ catalog và ghi lịch sử tra từ một lần. */
public class WordDetailViewModel extends ViewModel {

    private final DictionaryRepository repository = App.container().dictionaryRepository;
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
        load(id);
    }

    public void load(long id) {
        detail.setValue(Resource.loading());
        repository.detail(id, detail::postValue);
    }
}
