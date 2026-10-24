package com.example.nt118_project.ui.review;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.CardRequest;
import com.example.nt118_project.data.repository.DeckRepository;

/** Thêm, sửa, xóa một thẻ. Kết quả SUCCESS nghĩa là màn hình đóng lại. */
public class CardEditViewModel extends ViewModel {

    private final DeckRepository repository = App.container().deckRepository;
    private final MutableLiveData<Resource<Boolean>> state = new MutableLiveData<>();

    public LiveData<Resource<Boolean>> state() {
        return state;
    }

    private boolean busy() {
        Resource<Boolean> r = state.getValue();
        return r != null && r.isLoading();
    }

    /** cardId = null khi thêm mới vào deckId. */
    public void save(long deckId, @Nullable Long cardId, CardRequest body) {
        if (busy()) {
            return;
        }
        state.setValue(Resource.loading());
        if (cardId == null) {
            repository.addCard(deckId, body, r -> state.postValue(r.isSuccess()
                    ? Resource.success(true) : Resource.error(r.message, r.code)));
        } else {
            repository.updateCard(cardId, body, r -> state.postValue(r.isSuccess()
                    ? Resource.success(true) : Resource.error(r.message, r.code)));
        }
    }

    public void delete(long cardId) {
        if (busy()) {
            return;
        }
        state.setValue(Resource.loading());
        repository.deleteCard(cardId, r -> state.postValue(r.isSuccess()
                ? Resource.success(false) : Resource.error(r.message, r.code)));
    }

    public void consume() {
        state.setValue(null);
    }
}
