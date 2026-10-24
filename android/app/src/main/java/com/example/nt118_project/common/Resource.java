package com.example.nt118_project.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Kết quả một thao tác bất đồng bộ cho UI: đang tải, thành công kèm dữ liệu, hoặc lỗi kèm thông báo.
 * {@code code} là mã HTTP (0 nếu lỗi mạng) để màn hình xử lý riêng như 409 email trùng.
 */
public final class Resource<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    @NonNull
    public final Status status;
    @Nullable
    public final T data;
    @Nullable
    public final String message;
    public final int code;

    private Resource(@NonNull Status status, @Nullable T data, @Nullable String message, int code) {
        this.status = status;
        this.data = data;
        this.message = message;
        this.code = code;
    }

    public static <T> Resource<T> loading() {
        return new Resource<>(Status.LOADING, null, null, 0);
    }

    public static <T> Resource<T> success(@Nullable T data) {
        return new Resource<>(Status.SUCCESS, data, null, 200);
    }

    public static <T> Resource<T> error(String message, int code) {
        return new Resource<>(Status.ERROR, null, message, code);
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }
}
