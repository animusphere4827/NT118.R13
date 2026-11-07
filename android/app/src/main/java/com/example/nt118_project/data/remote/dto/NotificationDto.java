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

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NotificationDto)) return false;
        NotificationDto value = (NotificationDto) other;
        return id == value.id && isRead == value.isRead
                && java.util.Objects.equals(type, value.type)
                && java.util.Objects.equals(title, value.title)
                && java.util.Objects.equals(body, value.body)
                && java.util.Objects.equals(data, value.data)
                && java.util.Objects.equals(readAt, value.readAt)
                && java.util.Objects.equals(createdAt, value.createdAt);
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(id, type, title, body, data, isRead, readAt, createdAt);
    }

    @Nullable
    public String screen() {
        if (data == null) {
            return null;
        }
        JsonElement e = data.get("screen");
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
