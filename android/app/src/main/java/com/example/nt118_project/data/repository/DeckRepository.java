package com.example.nt118_project.data.repository;

import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.LearnApi;
import com.example.nt118_project.data.remote.dto.CardRequest;
import com.example.nt118_project.data.remote.dto.DeckDto;
import com.example.nt118_project.data.remote.dto.DeckRequest;
import com.example.nt118_project.data.remote.dto.IdResponse;

import java.util.Collections;
import java.util.List;

/** Bộ thẻ và thẻ flashcard (/learn/decks, /learn/cards). */
public class DeckRepository {

    private final LearnApi api;
    private final ApiCalls calls;

    public DeckRepository(LearnApi api, ApiCalls calls) {
        this.api = api;
        this.calls = calls;
    }

    public void decks(ApiCalls.Result<List<DeckDto>> result) {
        calls.enqueue(api.decks(), r -> result.onResult(r.isSuccess()
                ? Resource.success(r.data != null && r.data.rows != null ? r.data.rows : Collections.emptyList())
                : Resource.error(r.message, r.code)));
    }

    public void createDeck(String name, String description, ApiCalls.Result<IdResponse> result) {
        calls.enqueue(api.createDeck(new DeckRequest(name, description, null)), result);
    }

    /** Thêm từ trong từ điển vào bộ thẻ; server tự chép nghĩa, IPA, ví dụ và ảnh. */
    public void addVocabulary(long deckId, long vocabularyId, ApiCalls.Result<IdResponse> result) {
        calls.enqueue(api.addCard(deckId, CardRequest.fromVocabulary(vocabularyId)), result);
    }
}
