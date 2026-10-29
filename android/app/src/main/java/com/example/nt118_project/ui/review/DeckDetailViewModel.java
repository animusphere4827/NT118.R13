package com.example.nt118_project.ui.review;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Event;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.data.remote.dto.DeckDto;
import com.example.nt118_project.data.repository.DeckRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Chi tiết bộ thẻ: danh sách thẻ (thẻ ghim lên đầu), ghim/bỏ ghim, đổi tên, xóa bộ. */
public class DeckDetailViewModel extends ViewModel {

    private final DeckRepository repository = App.container().deckRepository;
    private final MutableLiveData<Resource<List<CardDto>>> cards = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> deleted = new MutableLiveData<>();
    private final MutableLiveData<String> name = new MutableLiveData<>();
    private long deckId;

    public void init(long deckId, String deckName) {
        if (this.deckId == 0) {
            this.deckId = deckId;
            name.setValue(deckName);
        }
    }

    public LiveData<Resource<List<CardDto>>> cards() {
        return cards;
    }

    public LiveData<String> name() {
        return name;
    }

    public LiveData<Event<String>> message() {
        return message;
    }

    public LiveData<Event<Boolean>> deleted() {
        return deleted;
    }

    public void load() {
        if (cards.getValue() == null) {
            cards.setValue(Resource.loading());
        }
        repository.allCards(deckId, false, r -> {
            if (r.isSuccess() && r.data != null) {
                List<CardDto> list = new ArrayList<>(r.data);
                // Thẻ ghim lên đầu, giữ thứ tự còn lại
                Collections.sort(list, (a, b) -> Boolean.compare(b.isPinned, a.isPinned));
                cards.postValue(Resource.success(list));
            } else {
                cards.postValue(r);
            }
        });
    }

    public void togglePin(CardDto card) {
        repository.setPinned(card, !card.isPinned, r -> {
            if (!r.isSuccess()) {
                message.postValue(new Event<>(r.message));
            }
            load();
        });
    }

    /**
     * PUT /learn/decks/{id} ghi đè cả mô tả và ảnh bìa (admin có thể đã đặt), màn này chỉ có id và tên
     * nên đọc lại bộ thẻ trước để giữ nguyên hai trường đó.
     */
    public void rename(String newName, String duplicateMessage) {
        repository.decks(list -> {
            if (!list.isSuccess() || list.data == null) {
                message.postValue(new Event<>(list.message));
                return;
            }
            DeckDto deck = null;
            for (DeckDto d : list.data) {
                if (d.id == deckId) {
                    deck = d;
                }
            }
            if (deck == null) {
                deleted.postValue(new Event<>(true));
                return;
            }
            repository.updateDeck(deckId, newName, deck.description, deck.coverImageUrl, r -> {
                if (r.isSuccess()) {
                    name.postValue(newName);
                } else {
                    message.postValue(new Event<>(r.code == 409 ? duplicateMessage : r.message));
                }
            });
        });
    }

    public void deleteDeck() {
        repository.deleteDeck(deckId, r -> {
            if (r.isSuccess()) {
                deleted.postValue(new Event<>(true));
            } else {
                message.postValue(new Event<>(r.message));
            }
        });
    }
}
