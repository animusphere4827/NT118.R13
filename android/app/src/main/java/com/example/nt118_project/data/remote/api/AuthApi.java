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
     * Đăng nhập Google: gửi Firebase ID token (không phải Google ID token).
     * 200/201 {token, refresh_token, user, is_new_user}; 401 token không hợp lệ;
     * 409 email đã liên kết tài khoản Google khác hoặc là tài khoản quản trị; 503 server chưa cấu hình Firebase.
     */
    @Headers(AuthInterceptor.NO_AUTH)
    @POST("auth/firebase")
    Call<AuthResponse> firebase(@Body GoogleLoginRequest body);

    /**
     * Đổi refresh token lấy cặp token mới; token cũ bị tiêu thụ. 401 refresh token hết hạn hoặc bị thu hồi.
     * Chỉ gọi từ TokenAuthenticator (tuần tự): gửi lại token đã dùng sẽ khiến server thu hồi cả phiên.
     */
    @Headers(AuthInterceptor.NO_AUTH)
    @POST("auth/refresh")
    Call<AuthResponse> refresh(@Body RefreshRequest body);

    @GET("auth/me")
    Call<UserDto> me();

    /** 204; thu hồi phiên theo refresh_token trong body, hoặc theo Bearer token nếu body rỗng. */
    @POST("auth/logout")
    Call<Void> logout(@Body RefreshRequest body);
}
