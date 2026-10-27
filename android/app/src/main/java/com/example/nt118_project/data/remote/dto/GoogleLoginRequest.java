package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST /auth/firebase: Firebase ID token có được sau khi đăng nhập Firebase bằng Google credential. */
public class GoogleLoginRequest {
    @SerializedName("id_token")
    public final String idToken;

    public GoogleLoginRequest(String idToken) {
        this.idToken = idToken;
    }
}
