package com.example.nt118_project.data.remote.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Body của POST /auth/refresh và POST /auth/logout: chỉ có refresh_token (server từ chối trường lạ).
 * refresh_token null thì Gson bỏ trường, gửi {} và server thu hồi phiên theo Bearer token.
 */
public class RefreshRequest {
    @SerializedName("refresh_token")
    @Nullable
    public final String refreshToken;

    public RefreshRequest(@Nullable String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
