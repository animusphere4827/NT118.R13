package com.example.nt118_project.data.remote.api;

import com.example.nt118_project.data.remote.dto.BadgeListResponse;
import com.example.nt118_project.data.remote.dto.FeatureBadgeRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;

/**
 * Huy hiệu. Server CHƯA có các endpoint này (Thành viên 1 đang làm);
 * khi AppConfig.fakeBadges = true app dùng FakeBadgeRepository.
 */
public interface BadgeApi {

    /** 200 {rows, total, unlocked_count, featured_code}, đã mở khóa đứng trước. */
    @GET("learn/badges")
    Call<BadgeListResponse> badges();

    /** 204; 404 mã không tồn tại; 409 huy hiệu chưa mở khóa. */
    @PUT("auth/me/featured-badge")
    Call<Void> feature(@Body FeatureBadgeRequest body);
}
