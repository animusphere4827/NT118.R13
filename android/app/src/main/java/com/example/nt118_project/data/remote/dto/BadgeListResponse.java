package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** {rows, total, unlocked_count, featured_code}. featured_code là huy hiệu đang gắn trên hồ sơ (có thể null). */
public class BadgeListResponse extends ListResponse<BadgeDto> {
    @SerializedName("unlocked_count")
    public int unlockedCount;
    @SerializedName("featured_code")
    public String featuredCode;
}
