package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** PUT /auth/me/featured-badge (giả định): {badge_code}, null để gỡ. */
public class FeatureBadgeRequest {
    @SerializedName("badge_code")
    public final String badgeCode;

    public FeatureBadgeRequest(String badgeCode) {
        this.badgeCode = badgeCode;
    }
}
