package com.example.nt118_project.ui.notification;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.nt118_project.App;
import com.example.nt118_project.common.Event;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.dto.NotificationDto;
import com.example.nt118_project.data.repository.NotificationRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Danh sách thông báo có phân trang, lọc chưa đọc, đánh dấu đã đọc, đọc tất cả, xóa. */
public class NotificationsViewModel extends ViewModel {

    private final NotificationRepository repository = App.container().notificationRepository;
    private final MutableLiveData<Resource<List<NotificationDto>>> items = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();
    private final List<NotificationDto> loaded = new ArrayList<>();
    private int page;
    private int total;
    private boolean loading;
    private boolean unreadOnly;
    /** Tăng mỗi lần tải lại từ đầu; response của lần tải cũ (đổi bộ lọc khi đang tải) bị bỏ qua. */
    private int generation;
    /** Thông báo đã vuốt xóa nhưng chưa gửi DELETE (còn chờ Hoàn tác), kèm vị trí cũ trong danh sách. */
    private final Map<Long, Integer> pendingDeletes = new LinkedHashMap<>();
    private final Map<Long, NotificationDto> pendingItems = new LinkedHashMap<>();

    public LiveData<Resource<List<NotificationDto>>> items() {
        return items;
    }

    public LiveData<Integer> unreadCount() {
        return repository.unreadCount();
    }

    public LiveData<Event<String>> message() {
        return message;
    }

    public boolean isUnreadOnly() {
        return unreadOnly;
    }

    public void reload() {
        generation++;
        loading = false;
        page = 0;
        total = 0;
        loaded.clear();
        if (items.getValue() == null) {
            items.setValue(Resource.loading());
        }
        loadMore();
    }

    public void toggleUnreadOnly() {
        unreadOnly = !unreadOnly;
        items.setValue(Resource.loading());
        reload();
    }

    public void loadMore() {
        if (loading || (page > 0 && loaded.size() >= total)) {
            return;
        }
        loading = true;
        int next = page + 1;
        int gen = generation;
        repository.page(next, unreadOnly, r -> {
            if (gen != generation) {
                return;
            }
            loading = false;
            if (r.isSuccess() && r.data != null) {
                page = next;
                total = r.data.total;
                if (unreadOnly) {
                    total = r.data.unreadCount;
                }
                for (NotificationDto n : r.data.rows) {
                    // Thông báo đang chờ Hoàn tác không hiện lại khi tải lại danh sách
                    if (!pendingDeletes.containsKey(n.id)) {
                        loaded.add(n);
                    }
                }
                items.postValue(Resource.success(new ArrayList<>(loaded)));
            } else if (page == 0) {
                items.postValue(Resource.error(r.message, r.code));
            } else {
                message.postValue(new Event<>(r.message));
            }
        });
    }

    public void markRead(NotificationDto n) {
        if (n.isRead) {
            return;
        }
        n.isRead = true;
        items.setValue(Resource.success(new ArrayList<>(loaded)));
        repository.markRead(n.id, r -> {
            if (!r.isSuccess()) {
                n.isRead = false;
                items.postValue(Resource.success(new ArrayList<>(loaded)));
            }
        });
    }

    public void readAll() {
        repository.readAll(r -> {
            if (r.isSuccess()) {
                reload();
            } else {
                message.postValue(new Event<>(r.message));
            }
        });
    }

    /** Ẩn khỏi danh sách ngay; chỉ gửi DELETE khi Snackbar đóng mà không bấm Hoàn tác ({@link #confirmDelete}). */
    public void delete(NotificationDto n) {
        int index = loaded.indexOf(n);
        if (index < 0) {
            return;
        }
        loaded.remove(index);
        pendingDeletes.put(n.id, index);
        pendingItems.put(n.id, n);
        total = Math.max(0, total - 1);
        items.setValue(Resource.success(new ArrayList<>(loaded)));
    }

    public void undoDelete(NotificationDto n) {
        Integer index = pendingDeletes.remove(n.id);
        pendingItems.remove(n.id);
        if (index == null) {
            return;
        }
        loaded.add(Math.min(index, loaded.size()), n);
        total++;
        items.setValue(Resource.success(new ArrayList<>(loaded)));
    }

    public void confirmDelete(NotificationDto n) {
        if (pendingDeletes.remove(n.id) == null) {
            return;
        }
        pendingItems.remove(n.id);
        repository.delete(n.id, r -> {
            if (!r.isSuccess()) {
                message.postValue(new Event<>(r.message));
                reload();
            } else if (!n.isRead) {
                repository.refreshUnread();
            }
        });
    }

    public boolean hasPendingDeletes() {
        return !pendingDeletes.isEmpty();
    }

    /** Rời màn khi Snackbar còn hiện: coi như không hoàn tác. */
    @Override
    protected void onCleared() {
        for (NotificationDto n : new ArrayList<>(pendingItems.values())) {
            confirmDelete(n);
        }
    }
}
