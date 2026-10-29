package com.example.nt118_project.data.remote.dto;

/** Kết quả MyMemory /get: {"responseData":{"translatedText":"..."},"responseStatus":200}. */
public class TranslationDto {
    public ResponseData responseData;
    /** Số hoặc chuỗi tùy trường hợp nên đọc dạng Object. */
    public Object responseStatus;

    public static class ResponseData {
        public String translatedText;
    }
}
