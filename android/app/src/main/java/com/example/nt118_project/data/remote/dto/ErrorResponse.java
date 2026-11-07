package com.example.nt118_project.data.remote.dto;

import java.util.List;

/** Thân lỗi chung của server: {"error": "...", "details": ["field: message"]}. */
public class ErrorResponse {
    public String error;
    @com.google.gson.annotations.SerializedName("error_code")
    public String errorCode;
    public List<String> details;
}
