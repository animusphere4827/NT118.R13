package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Phản hồi login, refresh và firebase: token (bí danh của access_token), refresh_token, user;
 * với Google thêm is_new_user. Access token sống 15 phút, refresh token tối đa 30 ngày.
 */
public class AuthResponse {
    public String token;
    @SerializedName("access_token")
    public String accessToken;
    @SerializedName("refresh_token")
    public String refreshToken;
    @SerializedName("expires_in")
    public long expiresIn;
    public UserDto user;
    @SerializedName("is_new_user")
    public boolean isNewUser;

    /** access_token nếu có, nếu không dùng token để tương thích server cũ. */
    public String accessToken() {
        return accessToken != null ? accessToken : token;
    }
}
