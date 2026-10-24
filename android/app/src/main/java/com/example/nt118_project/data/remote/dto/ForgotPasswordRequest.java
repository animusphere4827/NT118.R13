package com.example.nt118_project.data.remote.dto;

/** POST /auth/password/forgot (giả định, chờ server). */
public class ForgotPasswordRequest {
    public final String email;

    public ForgotPasswordRequest(String email) {
        this.email = email;
    }
}
