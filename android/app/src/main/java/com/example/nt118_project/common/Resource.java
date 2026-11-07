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
    public final String errorCode;

    private Resource(@NonNull Status status, @Nullable T data, @Nullable String message, int code, String errorCode) {
        this.status = status;
        this.data = data;
        this.message = message;
        this.code = code;
        this.errorCode = errorCode;
    }

    public static <T> Resource<T> loading() {
        return new Resource<>(Status.LOADING, null, null, 0, null);
    }

    public static <T> Resource<T> success(@Nullable T data) {
        return new Resource<>(Status.SUCCESS, data, null, 200, null);
    }

    public static <T> Resource<T> error(String message, int code) {
        return error(message, code, null);
    }

    public static <T> Resource<T> error(String message, int code, String errorCode) {
        return new Resource<>(Status.ERROR, null, message, code, errorCode);
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
