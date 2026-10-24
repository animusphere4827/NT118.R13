package com.example.nt118_project.data.repository;

import androidx.annotation.Nullable;

import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.LearnApi;
import com.example.nt118_project.data.remote.dto.ListResponse;
import com.example.nt118_project.data.remote.dto.LookupDto;
import com.example.nt118_project.data.remote.dto.LookupRequest;
import com.example.nt118_project.data.remote.dto.VocabularyDto;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;

/** Tra từ (GET /learn/dictionary), chi tiết từ và lịch sử tra từ (/learn/lookups). */
public class DictionaryRepository {

    public static final String SOURCE_DICTIONARY = "DICTIONARY";

    private final LearnApi api;
    private final ApiCalls calls;
    @Nullable
    private Call<ListResponse<VocabularyDto>> pendingSearch;

    public DictionaryRepository(LearnApi api, ApiCalls calls) {
        this.api = api;
        this.calls = calls;
    }

    /** Hủy lần tìm trước để kết quả cũ không ghi đè kết quả mới khi người dùng gõ nhanh. */
    public void search(String query, ApiCalls.Result<List<VocabularyDto>> result) {
        if (pendingSearch != null) {
            pendingSearch.cancel();
        }
        pendingSearch = api.searchDictionary(query);
        calls.enqueue(pendingSearch, r -> result.onResult(rows(r)));
    }

    public void detail(long id, ApiCalls.Result<VocabularyDto> result) {
        calls.enqueue(api.vocabulary(id), result);
    }

    public void history(ApiCalls.Result<List<LookupDto>> result) {
        calls.enqueue(api.lookups(1), r -> result.onResult(rows(r)));
    }

    /** Ghi lịch sử khi mở chi tiết một từ; lỗi không ảnh hưởng người dùng nên bỏ qua. */
    public void recordLookup(String keyword) {
        calls.enqueue(api.addLookup(new LookupRequest(keyword, SOURCE_DICTIONARY)), r -> { });
    }

    public void clearHistory(ApiCalls.Result<Void> result) {
        calls.enqueue(api.clearLookups(), result);
    }

    private static <T> Resource<List<T>> rows(Resource<ListResponse<T>> r) {
        if (!r.isSuccess()) {
            return Resource.error(r.message, r.code);
        }
        return Resource.success(r.data != null && r.data.rows != null ? r.data.rows : Collections.emptyList());
    }
}
