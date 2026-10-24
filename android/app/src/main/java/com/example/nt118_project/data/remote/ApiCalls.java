package com.example.nt118_project.data.remote;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.ErrorResponse;
import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Chuyển một {@link Call} của Retrofit thành {@link Resource} cho ViewModel, kèm thông báo lỗi tiếng Việt. */
public final class ApiCalls {

    /** Nhận kết quả cuối cùng (thành công hoặc lỗi). */
    public interface Result<T> {
        void onResult(Resource<T> result);
    }

    private final Context context;
    private final Gson gson;

    public ApiCalls(Context context, Gson gson) {
        this.context = context.getApplicationContext();
        this.gson = gson;
    }

    /** Chạy call bất đồng bộ, trả LiveData bắt đầu bằng LOADING. */
    public <T> LiveData<Resource<T>> asLiveData(Call<T> call) {
        MutableLiveData<Resource<T>> live = new MutableLiveData<>(Resource.loading());
        enqueue(call, live::postValue);
        return live;
    }

    public <T> void enqueue(Call<T> call, Result<T> result) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> c, @NonNull Response<T> response) {
                if (response.isSuccessful()) {
                    result.onResult(Resource.success(response.body()));
                } else {
                    result.onResult(Resource.error(messageOf(response), response.code()));
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) {
                if (c.isCanceled()) {
                    return;
                }
                result.onResult(Resource.error(messageOf(t), 0));
            }
        });
    }

    public String messageOf(Throwable t) {
        return context.getString(t instanceof IOException ? R.string.error_network : R.string.error_unknown);
    }

    /** Ưu tiên thông báo "error" từ server, nếu không có thì dùng câu mặc định theo mã HTTP. */
    public String messageOf(Response<?> response) {
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                ErrorResponse err = gson.fromJson(body.charStream(), ErrorResponse.class);
                if (err != null && err.error != null && !err.error.isEmpty()) {
                    return err.error;
                }
            }
        } catch (RuntimeException ignored) {
            // Thân lỗi không phải JSON (ví dụ trang lỗi của proxy)
        }
        int code = response.code();
        if (code == 401) {
            return context.getString(R.string.error_unauthorized);
        }
        if (code >= 500) {
            return context.getString(R.string.error_server);
        }
        return context.getString(R.string.error_unknown);
    }
}
