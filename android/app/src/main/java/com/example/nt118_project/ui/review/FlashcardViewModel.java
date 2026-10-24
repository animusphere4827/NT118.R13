package com.example.nt118_project.ui.review;

import androidx.annotation.Nullable;
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
import java.util.List;

/**
 * Phiên ôn thẻ. Lấy thẻ đến hạn của bộ; không có thẻ đến hạn thì ôn tự do toàn bộ thẻ.
 * "Ôn lại" (quality 1) đưa thẻ về cuối hàng đợi để gặp lại trong phiên, "Đã thuộc" (quality 5) chuyển thẻ kế.
 */
public class FlashcardViewModel extends ViewModel {

    public static final int QUALITY_AGAIN = 1;
    public static final int QUALITY_KNOWN = 5;

    private final DeckRepository repository = App.container().deckRepository;
    private final MutableLiveData<Resource<CardDto>> current = new MutableLiveData<>();
    private final MutableLiveData<Boolean> showBack = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> autoPlay = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();
    private final List<CardDto> queue = new ArrayList<>();
    private int index;
    private int total;
    private int known;
    private int again;
    private boolean freePractice;
    private long deckId;
    private String deckName = "";
    private boolean started;

    public LiveData<Resource<CardDto>> current() {
        return current;
    }

    public LiveData<Boolean> showBack() {
        return showBack;
    }

    public LiveData<Boolean> autoPlay() {
        return autoPlay;
    }

    public LiveData<Event<String>> message() {
        return message;
    }

    /** deckId = 0: chọn bộ có nhiều thẻ đến hạn nhất (mở từ màn Học). */
    public void start(long deckId, @Nullable String deckName) {
        if (started) {
            return;
        }
        started = true;
        this.deckId = deckId;
        this.deckName = deckName == null ? "" : deckName;
        current.setValue(Resource.loading());
        if (deckId > 0) {
            loadCards();
            return;
        }
        repository.decks(r -> {
            DeckDto best = null;
            if (r.isSuccess() && r.data != null) {
                for (DeckDto d : r.data) {
                    if (best == null || d.dueCount > best.dueCount) {
                        best = d;
                    }
                }
            }
            if (best == null) {
                current.postValue(Resource.error(null, 404));
                return;
            }
            this.deckId = best.id;
            this.deckName = best.name;
            loadCards();
        });
    }

    private void loadCards() {
        repository.allCards(deckId, true, due -> {
            if (due.isSuccess() && due.data != null && !due.data.isEmpty()) {
                begin(due.data, false);
            } else if (due.isSuccess()) {
                repository.allCards(deckId, false, all -> {
                    if (all.isSuccess() && all.data != null && !all.data.isEmpty()) {
                        begin(all.data, true);
                    } else {
                        current.postValue(all.isSuccess() ? Resource.error(null, 404) : Resource.error(all.message, all.code));
                    }
                });
            } else {
                current.postValue(Resource.error(due.message, due.code));
            }
        });
    }

    private void begin(List<CardDto> cards, boolean free) {
        queue.clear();
        queue.addAll(cards);
        total = cards.size();
        index = 0;
        freePractice = free;
        showBack.postValue(false);
        current.postValue(Resource.success(queue.get(0)));
    }

    public String deckName() {
        return deckName;
    }

    public long deckId() {
        return deckId;
    }

    public boolean isFreePractice() {
        return freePractice;
    }

    /** Vị trí thẻ hiện tại (bắt đầu từ 1) trong hàng đợi, kể cả thẻ được đưa lại cuối. */
    public int position() {
        return Math.min(index + 1, queue.size());
    }

    public int queueSize() {
        return queue.size();
    }

    public int remaining() {
        return Math.max(0, queue.size() - index);
    }

    public int knownCount() {
        return known;
    }

    public int againCount() {
        return again;
    }

    public boolean isFinished() {
        return total > 0 && index >= queue.size();
    }

    @Nullable
    public CardDto card() {
        return index < queue.size() ? queue.get(index) : null;
    }

    public void flip() {
        showBack.setValue(!Boolean.TRUE.equals(showBack.getValue()));
    }

    public void setAutoPlay(boolean on) {
        autoPlay.setValue(on);
    }

    /** Chấm thẻ hiện tại rồi chuyển thẻ kế. Gửi điểm lên server, lỗi chỉ báo nhẹ để không chặn phiên ôn. */
    public void grade(int quality) {
        CardDto card = card();
        if (card == null) {
            return;
        }
        repository.review(card.id, quality, r -> {
            if (!r.isSuccess()) {
                message.postValue(new Event<>(r.message));
            }
        });
        if (quality < 3) {
            again++;
            queue.add(card);
        } else {
            known++;
        }
        next();
    }

    /** Tự lật: sang thẻ kế mà không chấm điểm. */
    public void skip() {
        next();
    }

    /** Đổi thẻ trước rồi mới về mặt trước, để màn hình không lật lộ đáp án của thẻ cũ. */
    private void next() {
        index++;
        CardDto card = card();
        current.setValue(Resource.success(card));
        showBack.setValue(false);
    }
}
