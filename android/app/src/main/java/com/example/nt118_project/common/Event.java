package com.example.nt118_project.common;

import androidx.annotation.Nullable;

/** Gói giá trị LiveData chỉ được xử lý một lần (điều hướng, Toast) để không lặp lại khi xoay màn hình. */
public class Event<T> {

    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** Trả về nội dung nếu chưa xử lý, ngược lại trả null. */
    @Nullable
    public T consume() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }

    public T peek() {
        return content;
    }
}
