package com.example.nt118_project.ui.learning;

import androidx.annotation.Nullable;

/**
 * Tách từ tại vị trí chạm trong bài đọc, cùng quy tắc với server (reading.js):
 * chuỗi chữ/số, cho phép dấu ' ’ hoặc - nằm giữa hai chữ (don't, e-mail, follow-up).
 */
public final class ReadingWords {

    private ReadingWords() { }

    @Nullable
    public static String wordAt(@Nullable CharSequence text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return null;
        int length = text.length();
        int at = offset;
        // Chạm ngay sau chữ cuối của từ (offset rơi vào khoảng trắng/dấu câu) vẫn lấy từ đứng trước
        if (at == length || !isWordChar(text.charAt(at))) {
            if (at > 0 && isWordChar(text.charAt(at - 1))) at--;
            else return null;
        }
        int start = at;
        while (start > 0) {
            char before = text.charAt(start - 1);
            if (isWordChar(before)) start--;
            else if (isJoiner(before) && start - 2 >= 0 && isWordChar(text.charAt(start - 2))) start--;
            else break;
        }
        int end = at + 1;
        while (end < length) {
            char next = text.charAt(end);
            if (isWordChar(next)) end++;
            else if (isJoiner(next) && end + 1 < length && isWordChar(text.charAt(end + 1))) end++;
            else break;
        }
        String word = text.subSequence(start, end).toString().replace('’', '\'');
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) return word;
        }
        return null;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    private static boolean isJoiner(char c) {
        return c == '\'' || c == '’' || c == '-';
    }
}
