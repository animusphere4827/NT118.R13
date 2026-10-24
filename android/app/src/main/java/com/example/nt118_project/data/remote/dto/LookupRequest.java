package com.example.nt118_project.data.remote.dto;

/** POST /learn/lookups: source là DICTIONARY, READING hoặc FLASHCARD. */
public class LookupRequest {
    public final String keyword;
    public final String source;

    public LookupRequest(String keyword, String source) {
        this.keyword = keyword;
        this.source = source;
    }
}
