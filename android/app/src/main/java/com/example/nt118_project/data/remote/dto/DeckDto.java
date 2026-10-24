package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Bộ thẻ của người dùng (GET /learn/decks) kèm số thẻ, số thẻ đến hạn, số thẻ ghim. */
public class DeckDto {
    public long id;
    public String name;
    public String description;
    @SerializedName("cover_image_url")
    public String coverImageUrl;
    @SerializedName("card_count")
    public int cardCount;
    @SerializedName("due_count")
    public int dueCount;
    @SerializedName("pinned_count")
    public int pinnedCount;
    @SerializedName("updated_at")
    public String updatedAt;
}
