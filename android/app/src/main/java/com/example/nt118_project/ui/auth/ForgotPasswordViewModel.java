package com.example.nt118_project.ui.auth;

import android.os.SystemClock;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.ForgotPasswordResponse;
import com.example.nt118_project.data.remote.dto.VerifyOtpResponse;
import com.example.nt118_project.data.repository.PasswordRepository;

/** Trạng thái 3 bước quên mật khẩu. Mỗi màn hình dùng phần trạng thái của mình. */
public class ForgotPasswordViewModel extends ViewModel {

    private final PasswordRepository repository = App.container().passwordRepository;
    private final MutableLiveData<Resource<ForgotPasswordResponse>> sendState = new MutableLiveData<>();
    private final MutableLiveData<Resource<VerifyOtpResponse>> verifyState = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> resetState = new MutableLiveData<>();
    /** Mốc (elapsedRealtime) được phép gửi lại mã, giữ qua xoay màn hình. */
    private long resendAt;

    public LiveData<Resource<ForgotPasswordResponse>> sendState() {
        return sendState;
    }

    public LiveData<Resource<VerifyOtpResponse>> verifyState() {
        return verifyState;
    }

    public LiveData<Resource<Void>> resetState() {
        return resetState;
    }

    public void sendOtp(String email) {
        if (isLoading(sendState)) {
            return;
        }
        sendState.setValue(Resource.loading());
        repository.requestOtp(email, r -> {
            if (r.isSuccess() && r.data != null) {
                startResendTimer(r.data.resendAfter);
            }
            sendState.postValue(r);
        });
    }

    public void verify(String email, String otp) {
        if (isLoading(verifyState)) {
            return;
        }
        verifyState.setValue(Resource.loading());
        repository.verifyOtp(email, otp, verifyState::postValue);
    }

    public void reset(String resetToken, String password) {
        if (isLoading(resetState)) {
            return;
        }
        resetState.setValue(Resource.loading());
        repository.resetPassword(resetToken, password, resetState::postValue);
    }

    private boolean otpScreenStarted;

    /** true ở lần đầu mở màn OTP (không tính xoay màn hình hay quay lại từ bước 3). */
    public boolean markOtpScreenStarted() {
        if (otpScreenStarted) {
            return false;
        }
        otpScreenStarted = true;
        return true;
    }

    public void startResendTimer(int seconds) {
        resendAt = SystemClock.elapsedRealtime() + seconds * 1000L;
    }

    /** Số mili giây còn lại trước khi được gửi lại mã. */
    public long resendRemainingMs() {
        return Math.max(0, resendAt - SystemClock.elapsedRealtime());
    }

    public void consumeSend() {
        sendState.setValue(null);
    }

    public void consumeVerify() {
        verifyState.setValue(null);
    }

    public void consumeReset() {
        resetState.setValue(null);
    }

    private static boolean isLoading(LiveData<? extends Resource<?>> live) {
        Resource<?> r = live.getValue();
        return r != null && r.isLoading();
    }
}
