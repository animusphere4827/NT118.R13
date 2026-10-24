package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * PUT /auth/me (giả định): cập nhật hồ sơ. avatar_url là URL HTTP(S) hoặc null để xóa ảnh.
 * Email không đổi được ở đây. Trả 200 với người dùng sau khi cập nhật (giống GET /auth/me).
 */
public class UpdateProfileRequest {
    @SerializedName("full_name")
    public final String fullName;
    @SerializedName("target_score")
    public final int targetScore;
    @SerializedName("avatar_url")
    public final String avatarUrl;

    public UpdateProfileRequest(String fullName, int targetScore, String avatarUrl) {
        this.fullName = fullName;
        this.targetScore = targetScore;
        this.avatarUrl = avatarUrl;
    }
}
