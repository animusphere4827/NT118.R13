package com.example.nt118_project.data.remote.api;

import com.example.nt118_project.data.remote.AuthInterceptor;
import com.example.nt118_project.data.remote.dto.HealthDto;
import com.example.nt118_project.data.remote.dto.UserDto;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/** Nhóm /auth và /health. Các endpoint đăng nhập, đăng ký, Google được thêm ở M1.2. */
public interface AuthApi {

    @Headers(AuthInterceptor.NO_AUTH)
    @GET("health")
    Call<HealthDto> health();

    @GET("auth/me")
    Call<UserDto> me();

    @POST("auth/logout")
    Call<Void> logout();
}
