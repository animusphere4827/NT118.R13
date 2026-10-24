package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** 202 {expires_in, resend_after} tính bằng giây. Server trả 202 cả khi email không tồn tại. */
public class ForgotPasswordResponse {
    @SerializedName("expires_in")
    public int expiresIn = 300;
    @SerializedName("resend_after")
    public int resendAfter = 60;
}
