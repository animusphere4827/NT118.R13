package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST /auth/password/reset (giả định): {reset_token, password}, trả 204. */
public class ResetPasswordRequest {
    @SerializedName("reset_token")
    public final String resetToken;
    public final String password;

    public ResetPasswordRequest(String resetToken, String password) {
        this.resetToken = resetToken;
        this.password = password;
    }
}
