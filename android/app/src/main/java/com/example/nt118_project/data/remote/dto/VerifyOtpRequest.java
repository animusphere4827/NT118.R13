package com.example.nt118_project.data.remote.dto;

/** POST /auth/password/verify: {email, otp}. */
public class VerifyOtpRequest {
    public final String email;
    public final String otp;

    public VerifyOtpRequest(String email, String otp) {
        this.email = email;
        this.otp = otp;
    }
}
