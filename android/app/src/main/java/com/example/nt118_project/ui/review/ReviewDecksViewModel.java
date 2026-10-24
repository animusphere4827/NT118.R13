package com.example.nt118_project.ui.review;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Event;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.DeckDto;
import com.example.nt118_project.data.repository.DeckRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Tab Ôn tập: danh sách bộ thẻ, sắp xếp, tạo/đổi tên/xóa bộ thẻ. */
public class ReviewDecksViewModel extends ViewModel {

    public enum Sort { RECENT, NAME, DUE }

    private final DeckRepository repository = App.container().deckRepository;
    private final MutableLiveData<Resource<List<DeckDto>>> decks = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();
    private List<DeckDto> raw = new ArrayList<>();
    private Sort sort = Sort.RECENT;

    public LiveData<Resource<List<DeckDto>>> decks() {
        return decks;
    }

    /** Lỗi hoặc thông báo thao tác (đã có sẵn câu tiếng Việt từ server hoặc strings.xml). */
    public LiveData<Event<String>> message() {
        return message;
    }

    public void load() {
        if (decks.getValue() == null) {
            decks.setValue(Resource.loading());
        }
        repository.decks(r -> {
            if (r.isSuccess() && r.data != null) {
                raw = new ArrayList<>(r.data);
                decks.postValue(Resource.success(sorted()));
            } else {
                decks.postValue(r);
            }
        });
    }

    public void setSort(Sort sort) {
        this.sort = sort;
        decks.setValue(Resource.success(sorted()));
    }

    public Sort sort() {
        return sort;
    }

    public int totalDue() {
        int n = 0;
        for (DeckDto d : raw) {
            n += d.dueCount;
        }
        return n;
    }

    /** Bộ có nhiều thẻ đến hạn nhất, dùng cho "Bắt đầu ôn nhanh". */
    public DeckDto mostDue() {
        DeckDto best = null;
        for (DeckDto d : raw) {
            if (d.dueCount > 0 && (best == null || d.dueCount > best.dueCount)) {
                best = d;
            }
        }
        return best;
    }

    public void create(String name, String duplicateMessage) {
        repository.createDeck(name, "", r -> afterChange(r.isSuccess(), r.code == 409 ? duplicateMessage : r.message));
    }

    public void rename(DeckDto deck, String name, String duplicateMessage) {
        repository.updateDeck(deck.id, name, deck.description, deck.coverImageUrl,
                r -> afterChange(r.isSuccess(), r.code == 409 ? duplicateMessage : r.message));
    }

    public void delete(DeckDto deck) {
        repository.deleteDeck(deck.id, r -> afterChange(r.isSuccess(), r.message));
    }

    private void afterChange(boolean ok, String error) {
        if (!ok) {
            message.postValue(new Event<>(error));
        }
        load();
    }

    private List<DeckDto> sorted() {
        List<DeckDto> list = new ArrayList<>(raw);
        if (sort == Sort.NAME) {
            Collections.sort(list, Comparator.comparing(d -> d.name.toLowerCase(Locale.ROOT)));
        } else if (sort == Sort.DUE) {
            Collections.sort(list, (a, b) -> Integer.compare(b.dueCount, a.dueCount));
        }
        return list;
    }
}
