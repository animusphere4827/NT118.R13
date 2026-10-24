package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** POST /auth/register. Server trả 201 {id}, không kèm token. */
public class RegisterRequest {
    public final String email;
    public final String password;
    @SerializedName("full_name")
    public final String fullName;

    public RegisterRequest(String email, String password, String fullName) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }
}
