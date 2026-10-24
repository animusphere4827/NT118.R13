package com.example.nt118_project.data.remote.api;

import com.example.nt118_project.data.remote.dto.CardDto;
import com.example.nt118_project.data.remote.dto.CardRequest;
import com.example.nt118_project.data.remote.dto.DeckDto;
import com.example.nt118_project.data.remote.dto.DeckRequest;
import com.example.nt118_project.data.remote.dto.IdResponse;
import com.example.nt118_project.data.remote.dto.ListResponse;
import com.example.nt118_project.data.remote.dto.LookupDto;
import com.example.nt118_project.data.remote.dto.LookupRequest;
import com.example.nt118_project.data.remote.dto.VocabularyDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Nhóm /learn: từ điển, lịch sử tra từ, bộ thẻ (các endpoint đã có trên server). */
public interface LearnApi {

    /** Tối đa 20 từ bắt đầu bằng q, không phân biệt hoa thường. */
    @GET("learn/dictionary")
    Call<ListResponse<VocabularyDto>> searchDictionary(@Query("q") String q);

    @GET("learn/catalog/vocabularies/{id}")
    Call<VocabularyDto> vocabulary(@Path("id") long id);

    @GET("learn/lookups")
    Call<ListResponse<LookupDto>> lookups(@Query("page") int page);

    @POST("learn/lookups")
    Call<LookupDto> addLookup(@Body LookupRequest body);

    @DELETE("learn/lookups")
    Call<Void> clearLookups();

    @DELETE("learn/lookups/{id}")
    Call<Void> deleteLookup(@Path("id") long id);

    @GET("learn/decks")
    Call<ListResponse<DeckDto>> decks();

    /** 201 {id}; 409 trùng tên bộ thẻ. */
    @POST("learn/decks")
    Call<IdResponse> createDeck(@Body DeckRequest body);

    /** 204; 404 không thuộc người dùng; 409 trùng tên. */
    @PUT("learn/decks/{id}")
    Call<Void> updateDeck(@Path("id") long deckId, @Body DeckRequest body);

    /** 204, xóa luôn các thẻ trong bộ. */
    @DELETE("learn/decks/{id}")
    Call<Void> deleteDeck(@Path("id") long deckId);

    /** 20 thẻ mỗi trang; due=true chỉ lấy thẻ đến hạn, pinned=true chỉ lấy thẻ ghim. */
    @GET("learn/decks/{id}/cards")
    Call<ListResponse<CardDto>> cards(@Path("id") long deckId, @Query("page") int page,
                                      @Query("due") Boolean due, @Query("pinned") Boolean pinned);

    /** 201 {id}; 409 từ đã có trong bộ thẻ. */
    @POST("learn/decks/{id}/cards")
    Call<IdResponse> addCard(@Path("id") long deckId, @Body CardRequest body);

    /** 204; cần gửi đủ front_text, back_text, ipa, example_sentence, note, image_url, is_pinned. */
    @PUT("learn/cards/{id}")
    Call<Void> updateCard(@Path("id") long cardId, @Body CardRequest body);

    @DELETE("learn/cards/{id}")
    Call<Void> deleteCard(@Path("id") long cardId);
}
