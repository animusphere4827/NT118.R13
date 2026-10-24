package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Người dùng công khai, khớp publicUser() trong server/backend/app.js. */
public class UserDto {
    public long id;
    public String email;
    @SerializedName("full_name")
    public String fullName;
    @SerializedName("avatar_url")
    public String avatarUrl;
    public String role;
    public String status;
    @SerializedName("target_score")
    public Integer targetScore;
}
