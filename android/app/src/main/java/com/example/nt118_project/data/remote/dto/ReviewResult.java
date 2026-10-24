package com.example.nt118_project.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Kết quả chấm SM-2: hệ số dễ, khoảng cách ngày và lần ôn kế tiếp. */
public class ReviewResult {
    public long id;
    @SerializedName("ease_factor")
    public double easeFactor;
    @SerializedName("interval_days")
    public int intervalDays;
    public int repetitions;
    @SerializedName("due_at")
    public String dueAt;
}
