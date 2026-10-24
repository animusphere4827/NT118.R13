package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** {rows, total, unread_count, page, limit}. */
public class NotificationListResponse extends ListResponse<NotificationDto> {
    @SerializedName("unread_count")
    public int unreadCount;
}
