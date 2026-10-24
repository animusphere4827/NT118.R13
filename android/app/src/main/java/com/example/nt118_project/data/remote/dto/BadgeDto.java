package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Huy hiệu (giả định GET /learn/badges, chờ server).
 * icon: flame, book_open, mail, target, award, calendar_check, trending_up.
 */
public class BadgeDto {
    public String code;
    public String name;
    public String description;
    /** Điều kiện ngắn hiển thị dưới tên, ví dụ "5 ngày". */
    public String requirement;
    public String icon;
    public boolean unlocked;
    @SerializedName("unlocked_at")
    public String unlockedAt;
    public int progress;
    public int target;
}
