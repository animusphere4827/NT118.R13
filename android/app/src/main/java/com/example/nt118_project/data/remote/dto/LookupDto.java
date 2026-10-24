package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Một dòng lịch sử tra từ (GET /learn/lookups). */
public class LookupDto {
    public long id;
    public String keyword;
    public String source;
    @SerializedName("lookup_count")
    public int lookupCount;
    @SerializedName("vocabulary_id")
    public Long vocabularyId;
    public String ipa;
    @SerializedName("meaning_vi")
    public String meaningVi;
}
