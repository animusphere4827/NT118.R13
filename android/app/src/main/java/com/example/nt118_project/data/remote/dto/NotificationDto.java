package com.example.nt118_project.data.remote.dto;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/** Thông báo (GET /learn/notifications). type: SYSTEM, STUDY_REMINDER, NEW_CONTENT, RESULT, ACHIEVEMENT. */
public class NotificationDto {
    public long id;
    public String type;
    public String title;
    public String body;
    /** Dữ liệu điều hướng tùy chọn, ví dụ {"screen":"mock_result","attempt_id":12}. */
    public JsonObject data;
    @SerializedName("is_read")
    public boolean isRead;
    @SerializedName("read_at")
    public String readAt;
    @SerializedName("created_at")
    public String createdAt;

    @Nullable
    public String screen() {
        if (data == null) {
            return null;
        }
        JsonElement e = data.get("screen");
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
