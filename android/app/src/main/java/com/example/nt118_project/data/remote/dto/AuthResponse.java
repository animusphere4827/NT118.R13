package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Phản hồi đăng nhập: {token, user} và với Google thêm is_new_user. */
public class AuthResponse {
    public String token;
    public UserDto user;
    @SerializedName("is_new_user")
    public boolean isNewUser;
}
