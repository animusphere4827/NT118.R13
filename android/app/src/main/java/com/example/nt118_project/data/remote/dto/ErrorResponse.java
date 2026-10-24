package com.example.nt118_project.data.remote.dto;

import java.util.List;

/** Thân lỗi chung của server: {"error": "...", "details": ["field: message"]}. */
public class ErrorResponse {
    public String error;
    public List<String> details;
}
