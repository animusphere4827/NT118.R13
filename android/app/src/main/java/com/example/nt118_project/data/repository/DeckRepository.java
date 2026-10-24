package com.example.nt118_project.data.repository;

import androidx.annotation.Nullable;

import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.LearnApi;
import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.data.remote.dto.CardRequest;
import com.example.nt118_project.data.remote.dto.DeckDto;
import com.example.nt118_project.data.remote.dto.DeckRequest;
import com.example.nt118_project.data.remote.dto.IdResponse;
import com.example.nt118_project.data.remote.dto.ListResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bộ thẻ và thẻ flashcard (/learn/decks, /learn/cards). */
public class DeckRepository {

    /** Giới hạn số trang tải khi lấy hết thẻ của một bộ (20 thẻ/trang). */
    private static final int MAX_PAGES = 50;

    private final LearnApi api;
    private final ApiCalls calls;

    public DeckRepository(LearnApi api, ApiCalls calls) {
        this.api = api;
        this.calls = calls;
    }

    // ---------- Bộ thẻ ----------

    public void decks(ApiCalls.Result<List<DeckDto>> result) {
        calls.enqueue(api.decks(), r -> result.onResult(rows(r)));
    }

    public void createDeck(String name, String description, ApiCalls.Result<IdResponse> result) {
        calls.enqueue(api.createDeck(new DeckRequest(name, description, null)), result);
    }

    public void updateDeck(long deckId, String name, String description, @Nullable String coverUrl,
                           ApiCalls.Result<Void> result) {
        calls.enqueue(api.updateDeck(deckId, new DeckRequest(name, description, coverUrl)), result);
    }

    public void deleteDeck(long deckId, ApiCalls.Result<Void> result) {
        calls.enqueue(api.deleteDeck(deckId), result);
    }

    // ---------- Thẻ ----------

    /** Lấy hết thẻ của bộ (lần lượt từng trang). dueOnly = chỉ thẻ đến hạn, sắp theo hạn ôn. */
    public void allCards(long deckId, boolean dueOnly, ApiCalls.Result<List<CardDto>> result) {
        loadPage(deckId, dueOnly, 1, new ArrayList<>(), result);
    }

    private void loadPage(long deckId, boolean dueOnly, int page, List<CardDto> acc,
                          ApiCalls.Result<List<CardDto>> result) {
        calls.enqueue(api.cards(deckId, page, dueOnly ? true : null, null), r -> {
            if (!r.isSuccess() || r.data == null) {
                result.onResult(Resource.error(r.message, r.code));
                return;
            }
            List<CardDto> rows = r.data.rows != null ? r.data.rows : Collections.emptyList();
            acc.addAll(rows);
            boolean more = r.data.limit > 0 && rows.size() >= r.data.limit && acc.size() < r.data.total;
            if (more && page < MAX_PAGES) {
                loadPage(deckId, dueOnly, page + 1, acc, result);
            } else {
                result.onResult(Resource.success(acc));
            }
        });
    }

    public void addCard(long deckId, CardRequest card, ApiCalls.Result<IdResponse> result) {
        calls.enqueue(api.addCard(deckId, card), result);
    }

    /** Thêm từ trong từ điển vào bộ thẻ; server tự chép nghĩa, IPA, ví dụ và ảnh. */
    public void addVocabulary(long deckId, long vocabularyId, ApiCalls.Result<IdResponse> result) {
        addCard(deckId, CardRequest.fromVocabulary(vocabularyId), result);
    }

    public void updateCard(long cardId, CardRequest card, ApiCalls.Result<Void> result) {
        calls.enqueue(api.updateCard(cardId, card), result);
    }

    /** Ghim/bỏ ghim: API sửa thẻ cần đủ trường nên gửi lại toàn bộ thẻ. */
    public void setPinned(CardDto card, boolean pinned, ApiCalls.Result<Void> result) {
        CardRequest body = card.toRequest();
        body.isPinned = pinned;
        updateCard(card.id, body, result);
    }

    public void deleteCard(long cardId, ApiCalls.Result<Void> result) {
        calls.enqueue(api.deleteCard(cardId), result);
    }

    private static <T> Resource<List<T>> rows(Resource<ListResponse<T>> r) {
        if (!r.isSuccess()) {
            return Resource.error(r.message, r.code);
        }
        return Resource.success(r.data != null && r.data.rows != null ? r.data.rows : Collections.emptyList());
    }
}
