package com.example.nt118_project.data.remote.api;

import com.example.nt118_project.data.remote.AuthInterceptor;
import com.example.nt118_project.data.remote.dto.ForgotPasswordRequest;
import com.example.nt118_project.data.remote.dto.ForgotPasswordResponse;
import com.example.nt118_project.data.remote.dto.ResetPasswordRequest;
import com.example.nt118_project.data.remote.dto.UpdateProfileRequest;
import com.example.nt118_project.data.remote.dto.UserDto;
import com.example.nt118_project.data.remote.dto.VerifyOtpRequest;
import com.example.nt118_project.data.remote.dto.VerifyOtpResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;

/**
 * Quên mật khẩu và cập nhật hồ sơ (B1.3 của Thành viên 1). FakePasswordRepository /
 * FakeProfileRepository chỉ dùng khi bật cờ fake.* trong local_config.properties.
 */
public interface AccountApi {

    /** 202 {expires_in, resend_after}; 422 email sai định dạng; 429 gửi quá nhiều; 503 server chưa cấu hình email. */
    @Headers(AuthInterceptor.NO_AUTH)
    @POST("auth/password/forgot")
    Call<ForgotPasswordResponse> forgot(@Body ForgotPasswordRequest body);

    /** 200 {reset_token, expires_in}; 400 mã sai hoặc hết hạn; 429 nhập sai quá nhiều. */
    @Headers(AuthInterceptor.NO_AUTH)
    @POST("auth/password/verify")
    Call<VerifyOtpResponse> verify(@Body VerifyOtpRequest body);

    /** 204; 400 reset_token hết hạn; 422 mật khẩu không hợp lệ. */
    @Headers(AuthInterceptor.NO_AUTH)
    @POST("auth/password/reset")
    Call<Void> reset(@Body ResetPasswordRequest body);

    /** 200 người dùng sau khi cập nhật; 422 dữ liệu không hợp lệ. */
    @PUT("auth/me")
    Call<UserDto> updateProfile(@Body UpdateProfileRequest body);
}
