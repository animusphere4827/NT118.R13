package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Tạo/sửa thẻ. Khi tạo có thể chỉ gửi vocabulary_id, server tự chép từ, nghĩa, IPA, ví dụ, ảnh từ từ điển.
 * Trường null không được Gson gửi đi.
 */
public class CardRequest {
    @SerializedName("vocabulary_id")
    public Long vocabularyId;
    @SerializedName("front_text")
    public String frontText;
    @SerializedName("back_text")
    public String backText;
    public String ipa;
    @SerializedName("example_sentence")
    public String exampleSentence;
    public String note;
    @SerializedName("image_url")
    public String imageUrl;
    @SerializedName("is_pinned")
    public Boolean isPinned;

    public static CardRequest fromVocabulary(long vocabularyId) {
        CardRequest r = new CardRequest();
        r.vocabularyId = vocabularyId;
        return r;
    }
}
