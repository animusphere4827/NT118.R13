package com.example.nt118_project.data.remote.api;

import com.example.nt118_project.data.remote.AuthInterceptor;
import com.example.nt118_project.data.remote.dto.AuthResponse;
import com.example.nt118_project.data.remote.dto.GoogleLoginRequest;
import com.example.nt118_project.data.remote.dto.HealthDto;
import com.example.nt118_project.data.remote.dto.IdResponse;
import com.example.nt118_project.data.remote.dto.LoginRequest;
import com.example.nt118_project.data.remote.dto.RefreshRequest;
import com.example.nt118_project.data.remote.dto.RegisterRequest;
import com.example.nt118_project.data.remote.dto.UserDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/** Nhóm /auth và /health theo docs/openapi.yaml. */
public interface AuthApi {

    @Headers(AuthInterceptor.NO_AUTH)
    @GET("health")
    Call<HealthDto> health();

    /** 201 {id}; 409 email đã tồn tại; 422 dữ liệu không hợp lệ (mật khẩu tối thiểu 12 ký tự). */
    @Headers(AuthInterceptor.NO_AUTH)
    @POST("auth/register")
    Call<IdResponse> register(@Body RegisterRequest body);

    /** 200 {token, access_token, refresh_token, user}; 401 sai thông tin hoặc tài khoản bị khóa. */
    @Headers(AuthInterceptor.NO_AUTH)
    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    /**
     * Đăng nhập Google qua server (thay cho /auth/firebase). Chưa có trên server:
     * khi AppConfig.fakeGoogleAuth = true app dùng FakeGoogleAuthSource thay vì gọi endpoint này.
     * 200/201 {token, user, is_new_user}; 401 token không hợp lệ; 409 email đã liên kết tài khoản Google khác.
     */
    @Headers(AuthInterceptor.NO_AUTH)
    @POST("auth/google")
    Call<AuthResponse> google(@Body GoogleLoginRequest body);

    /**
     * Đổi refresh token lấy cặp token mới; token cũ bị tiêu thụ. 401 refresh token hết hạn hoặc bị thu hồi.
     * Chỉ gọi từ TokenAuthenticator (tuần tự): gửi lại token đã dùng sẽ khiến server thu hồi cả phiên.
     */
    @Headers(AuthInterceptor.NO_AUTH)
    @POST("auth/refresh")
    Call<AuthResponse> refresh(@Body RefreshRequest body);

    @GET("auth/me")
    Call<UserDto> me();

    @POST("auth/logout")
    Call<Void> logout();
}
