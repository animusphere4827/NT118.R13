package com.example.nt118_project.data.repository;

import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.AccountApi;
import com.example.nt118_project.data.remote.dto.ForgotPasswordRequest;
import com.example.nt118_project.data.remote.dto.ForgotPasswordResponse;
import com.example.nt118_project.data.remote.dto.ResetPasswordRequest;
import com.example.nt118_project.data.remote.dto.VerifyOtpRequest;
import com.example.nt118_project.data.remote.dto.VerifyOtpResponse;

/** Quên mật khẩu 3 bước: gửi OTP qua email, xác nhận OTP, đặt mật khẩu mới. */
public interface PasswordRepository {

    void requestOtp(String email, ApiCalls.Result<ForgotPasswordResponse> result);

    void verifyOtp(String email, String otp, ApiCalls.Result<VerifyOtpResponse> result);

    void resetPassword(String resetToken, String password, ApiCalls.Result<Void> result);

    class Remote implements PasswordRepository {
        private final AccountApi api;
        private final ApiCalls calls;

        public Remote(AccountApi api, ApiCalls calls) {
            this.api = api;
            this.calls = calls;
        }

        @Override
        public void requestOtp(String email, ApiCalls.Result<ForgotPasswordResponse> result) {
            calls.enqueue(api.forgot(new ForgotPasswordRequest(email)), result);
        }

        @Override
        public void verifyOtp(String email, String otp, ApiCalls.Result<VerifyOtpResponse> result) {
            calls.enqueue(api.verify(new VerifyOtpRequest(email, otp)), result);
        }

        @Override
        public void resetPassword(String resetToken, String password, ApiCalls.Result<Void> result) {
            calls.enqueue(api.reset(new ResetPasswordRequest(resetToken, password)), result);
        }
    }
}
