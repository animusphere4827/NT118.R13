package com.example.nt118_project.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.nt118_project.data.local.SessionManager;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.NotificationApi;
import com.example.nt118_project.data.remote.dto.NotificationListResponse;
import com.example.nt118_project.data.remote.dto.UpdatedResponse;

/** Thông báo và số chưa đọc (dùng cho badge trên chuông). */
public class NotificationRepository {

    private final NotificationApi api;
    private final ApiCalls calls;
    private final SessionManager session;
    private final MutableLiveData<Integer> unread = new MutableLiveData<>(0);

    public NotificationRepository(NotificationApi api, ApiCalls calls, SessionManager session) {
        this.api = api;
        this.calls = calls;
        this.session = session;
    }

    public LiveData<Integer> unreadCount() {
        return unread;
    }

    public void page(int page, boolean unreadOnly, ApiCalls.Result<NotificationListResponse> result) {
        calls.enqueue(api.list(page, unreadOnly ? true : null), r -> {
            if (r.isSuccess() && r.data != null) {
                unread.postValue(r.data.unreadCount);
            }
            result.onResult(r);
        });
    }

    /** Chỉ cập nhật số chưa đọc (gọi khi mở trang chủ). */
    public void refreshUnread() {
        if (!session.isLoggedIn() || session.isFakeSession()) {
            unread.postValue(0);
            return;
        }
        calls.enqueue(api.list(1, true), r -> {
            if (r.isSuccess() && r.data != null) {
                unread.postValue(r.data.unreadCount);
            }
        });
    }

    public void markRead(long id, ApiCalls.Result<Void> result) {
        calls.enqueue(api.markRead(id), r -> {
            if (r.isSuccess()) {
                Integer n = unread.getValue();
                unread.postValue(Math.max(0, (n == null ? 0 : n) - 1));
            }
            result.onResult(r);
        });
    }

    public void readAll(ApiCalls.Result<UpdatedResponse> result) {
        calls.enqueue(api.readAll(), r -> {
            if (r.isSuccess()) {
                unread.postValue(0);
            }
            result.onResult(r);
        });
    }

    public void delete(long id, ApiCalls.Result<Void> result) {
        calls.enqueue(api.delete(id), result);
    }
}
