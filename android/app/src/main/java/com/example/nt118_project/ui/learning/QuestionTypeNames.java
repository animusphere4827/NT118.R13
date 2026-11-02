package com.example.nt118_project.ui.learning;

import androidx.annotation.NonNull;

/** Tên tiếng Việt cho question_type của câu Reading và type của câu quiz. */
final class QuestionTypeNames {

    private QuestionTypeNames() {
    }

    @NonNull
    static String of(@NonNull String type) {
        switch (type) {
            case "WORD_FORM": return "Từ loại";
            case "VERB_TENSE": return "Thì của động từ";
            case "PREPOSITION": return "Giới từ";
            case "CONJUNCTION": return "Liên từ";
            case "PRONOUN": return "Đại từ";
            case "VOCABULARY":
            case "VOCAB_IN_CONTEXT": return "Từ vựng";
            case "COLLOCATION": return "Kết hợp từ";
            case "SENTENCE_INSERTION": return "Chèn câu";
            case "MAIN_IDEA": return "Ý chính";
            case "DETAIL": return "Chi tiết";
            case "INFERENCE": return "Suy luận";
            case "NOT_TRUE": return "Thông tin sai";
            case "PURPOSE": return "Mục đích";
            case "FILL_BLANK": return "Điền từ";
            case "MATCH": return "Ghép nghĩa";
            case "ORDER": return "Sắp xếp câu";
            default: return "Đọc hiểu";
        }
    }
}
