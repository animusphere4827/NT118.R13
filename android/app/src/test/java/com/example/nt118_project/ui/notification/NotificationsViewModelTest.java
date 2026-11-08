package com.example.nt118_project.ui.notification;

import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.dto.NotificationDto;
import com.example.nt118_project.data.remote.dto.NotificationListResponse;
import com.example.nt118_project.data.repository.NotificationRepository;
import com.example.nt118_project.support.ImmediateTaskTest;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationsViewModelTest extends ImmediateTaskTest {
    @Test public void readWaitsForServerAndDoesNotMutatePreviousList() {
        Repository repository = new Repository();
        NotificationsViewModel model = new NotificationsViewModel(repository);
        NotificationDto notification = notification(1);
        loadPage(model, repository, 40, notification, notification(2));
        model.markRead(notification);
        model.markRead(notification);
        assertEquals(1, repository.requests);
        assertFalse(rows(model).get(0).isRead);
        repository.pending.onResult(Resource.success(null));
        assertFalse(notification.isRead);
        assertTrue(rows(model).get(0).isRead);
        assertNotNull(rows(model).get(0).readAt);
    }

    @Test public void readKeepsLoadedPagesInsteadOfReloading() {
        Repository repository = new Repository();
        NotificationsViewModel model = new NotificationsViewModel(repository);
        loadPage(model, repository, 40, notification(1), notification(2));
        model.loadMore();
        repository.page.onResult(Resource.success(response(40, notification(3))));
        assertEquals(3, rows(model).size());
        model.markRead(rows(model).get(2));
        repository.pending.onResult(Resource.success(null));
        assertEquals(2, repository.loads);
        assertEquals(3, rows(model).size());
        assertTrue(rows(model).get(2).isRead);
    }

    @Test public void failedReadDoesNotMarkNotificationAndCanBeRetried() {
        Repository repository = new Repository();
        NotificationsViewModel model = new NotificationsViewModel(repository);
        NotificationDto notification = notification(1);
        loadPage(model, repository, 1, notification);
        model.markRead(notification);
        repository.pending.onResult(Resource.error("offline", 0));
        assertFalse(rows(model).get(0).isRead);
        assertEquals(1, repository.loads);
        assertNotNull(model.message().getValue());
        model.markRead(notification);
        assertEquals(2, repository.requests);
    }

    private static void loadPage(NotificationsViewModel model, Repository repository, int total, NotificationDto... rows) {
        model.reload();
        repository.page.onResult(Resource.success(response(total, rows)));
    }

    private static NotificationListResponse response(int total, NotificationDto... rows) {
        NotificationListResponse response = new NotificationListResponse();
        response.total = total;
        response.rows.addAll(java.util.Arrays.asList(rows));
        return response;
    }

    private static NotificationDto notification(long id) {
        NotificationDto notification = new NotificationDto();
        notification.id = id;
        return notification;
    }

    private static List<NotificationDto> rows(NotificationsViewModel model) {
        return model.items().getValue().data;
    }

    private static final class Repository extends NotificationRepository {
        int requests, loads;
        ApiCalls.Result<Void> pending;
        ApiCalls.Result<NotificationListResponse> page;
        Repository() { super(null, null, null); }
        @Override public void markRead(long id, ApiCalls.Result<Void> result) { requests++; pending = result; }
        @Override public void page(int page, boolean unread, ApiCalls.Result<NotificationListResponse> result) { loads++; this.page = result; }
    }
}
