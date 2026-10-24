package com.example.nt118_project.data.repository.fake;

import android.os.Handler;
import android.os.Looper;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.dto.ForgotPasswordResponse;
import com.example.nt118_project.data.remote.dto.VerifyOtpResponse;
import com.example.nt118_project.data.repository.PasswordRepository;

/** Giả lập quên mật khẩu. Mã OTP đúng luôn là {@link #OTP}; mọi email đều "được gửi". */
public class FakePasswordRepository implements PasswordRepository {

    public static final String OTP = "123456";
    private static final String RESET_TOKEN = "fake-reset-token";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ApiCalls calls;

    public FakePasswordRepository(ApiCalls calls) {
        this.calls = calls;
    }

    @Override
    public void requestOtp(String email, ApiCalls.Result<ForgotPasswordResponse> result) {
        later(() -> result.onResult(Resource.success(new ForgotPasswordResponse())));
    }

    @Override
    public void verifyOtp(String email, String otp, ApiCalls.Result<VerifyOtpResponse> result) {
        later(() -> {
            if (OTP.equals(otp)) {
                VerifyOtpResponse r = new VerifyOtpResponse();
                r.resetToken = RESET_TOKEN;
                r.expiresIn = 600;
                result.onResult(Resource.success(r));
            } else {
                result.onResult(Resource.error(calls.string(R.string.error_otp_invalid), 400));
            }
        });
    }

    @Override
    public void resetPassword(String resetToken, String password, ApiCalls.Result<Void> result) {
        later(() -> result.onResult(RESET_TOKEN.equals(resetToken)
                ? Resource.success(null)
                : Resource.error(calls.string(R.string.error_reset_expired), 400)));
    }

    private void later(Runnable r) {
        main.postDelayed(r, 600);
    }
}
