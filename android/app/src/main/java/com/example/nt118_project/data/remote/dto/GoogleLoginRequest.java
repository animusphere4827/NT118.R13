package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST /auth/firebase: Firebase ID token có được sau khi đăng nhập Firebase bằng Google credential. */
public class GoogleLoginRequest {
    @SerializedName("id_token")
    public final String idToken;

    @SerializedName("existing_password")
    public final String existingPassword;

    public GoogleLoginRequest(String idToken) {
        this(idToken, null);
    }

    public GoogleLoginRequest(String idToken, String existingPassword) {
        this.idToken = idToken;
        this.existingPassword = existingPassword;
    }
}
