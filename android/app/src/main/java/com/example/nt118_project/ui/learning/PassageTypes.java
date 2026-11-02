package com.example.nt118_project.ui.learning;

import androidx.annotation.NonNull;

/** Loại văn bản Reading (passage_type trên server) và tên hiển thị. */
final class PassageTypes {

    /** Thứ tự chip lọc ở thư viện đọc; OTHER không có chip riêng. */
    static final String[] FILTERS = {"EMAIL", "NOTICE", "ARTICLE", "ADVERTISEMENT", "LETTER", "MEMO",
            "SCHEDULE", "FORM", "INVOICE", "CHAT", "WEB_PAGE", "REVIEW"};

    private PassageTypes() {
    }

    @NonNull
    static String label(@NonNull String type) {
        switch (type) {
            case "EMAIL": return "Email";
            case "NOTICE": return "Thông báo";
            case "ARTICLE": return "Bài viết";
            case "ADVERTISEMENT": return "Quảng cáo";
            case "LETTER": return "Thư";
            case "MEMO": return "Ghi nhớ";
            case "SCHEDULE": return "Lịch trình";
            case "FORM": return "Biểu mẫu";
            case "INVOICE": return "Hóa đơn";
            case "CHAT": return "Tin nhắn";
            case "WEB_PAGE": return "Trang web";
            case "REVIEW": return "Đánh giá";
            default: return "Bài đọc";
        }
    }
}
