package com.example.nt118_project.data.remote.api;

import com.example.nt118_project.data.remote.dto.NotificationListResponse;
import com.example.nt118_project.data.remote.dto.UpdatedResponse;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** /learn/notifications: 20 thông báo mỗi trang, mới nhất trước. */
public interface NotificationApi {

    @GET("learn/notifications")
    Call<NotificationListResponse> list(@Query("page") int page, @Query("unread") Boolean unreadOnly);

    @PATCH("learn/notifications/{id}/read")
    Call<Void> markRead(@Path("id") long id);

    @POST("learn/notifications/read-all")
    Call<UpdatedResponse> readAll();

    @DELETE("learn/notifications/{id}")
    Call<Void> delete(@Path("id") long id);
}
