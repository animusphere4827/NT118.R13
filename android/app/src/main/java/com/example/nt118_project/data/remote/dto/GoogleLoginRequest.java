package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST /auth/google: ID token lấy từ Credential Manager (GoogleIdTokenCredential.getIdToken()). */
public class GoogleLoginRequest {
    @SerializedName("id_token")
    public final String idToken;

    public GoogleLoginRequest(String idToken) {
        this.idToken = idToken;
    }
}
