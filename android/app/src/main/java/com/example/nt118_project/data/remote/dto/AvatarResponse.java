package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** 200 của POST /auth/me/avatar: URL ảnh mới (đã cắt 512x512 WebP) và người dùng sau khi cập nhật. */
public class AvatarResponse {
    @SerializedName("avatar_url")
    public String avatarUrl;
    public UserDto user;
}
