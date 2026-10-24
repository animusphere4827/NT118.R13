package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** 200 {reset_token, expires_in}: token dùng một lần cho bước đặt mật khẩu mới. */
public class VerifyOtpResponse {
    @SerializedName("reset_token")
    public String resetToken;
    @SerializedName("expires_in")
    public int expiresIn;
}
