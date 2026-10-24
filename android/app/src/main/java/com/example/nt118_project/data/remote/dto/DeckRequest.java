package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST/PUT /learn/decks: {name, description, cover_image_url}. */
public class DeckRequest {
    public final String name;
    public final String description;
    @SerializedName("cover_image_url")
    public final String coverImageUrl;

    public DeckRequest(String name, String description, String coverImageUrl) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.coverImageUrl = coverImageUrl;
    }
}
