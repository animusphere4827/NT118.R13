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

    /** Thẻ từ một từ ngoài DB (từ điển mở): mặt sau là nghĩa tiếng Việt, thiếu thì dùng định nghĩa tiếng Anh. */
    public static CardRequest fromExternal(VocabularyDto w) {
        CardRequest r = new CardRequest();
        r.frontText = cut(w.word, 500);
        String back = w.meaningVi != null ? w.meaningVi : w.definitionEn;
        r.backText = cut(back != null ? back : w.word, 5000);
        r.ipa = cut(w.ipa, 150);
        r.exampleSentence = cut(w.exampleSentence, 2000);
        r.note = w.meaningVi != null ? cut(w.definitionEn, 2000) : null;
        return r;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
