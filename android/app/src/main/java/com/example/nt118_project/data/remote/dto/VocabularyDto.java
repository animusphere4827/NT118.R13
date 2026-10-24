package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Từ vựng. GET /learn/dictionary trả các trường rút gọn (có topic);
 * GET /learn/catalog/vocabularies/{id} trả đủ trường (definition_en, ví dụ, đồng nghĩa...).
 */
public class VocabularyDto {
    public long id;
    public String word;
    public String ipa;
    @SerializedName("part_of_speech")
    public String partOfSpeech;
    @SerializedName("meaning_vi")
    public String meaningVi;
    @SerializedName("definition_en")
    public String definitionEn;
    @SerializedName("example_sentence")
    public String exampleSentence;
    @SerializedName("example_vi")
    public String exampleVi;
    public String synonyms;
    public String antonyms;
    @SerializedName("image_url")
    public String imageUrl;
    @SerializedName("audio_url")
    public String audioUrl;
    /** Tên chủ đề, chỉ có trong kết quả tra từ. */
    public String topic;
}
