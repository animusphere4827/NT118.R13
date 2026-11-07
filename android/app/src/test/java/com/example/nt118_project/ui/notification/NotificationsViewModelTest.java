package com.example.nt118_project.ui.notification;

import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.dto.NotificationDto;
import com.example.nt118_project.data.remote.dto.NotificationListResponse;
import com.example.nt118_project.data.repository.NotificationRepository;
import com.example.nt118_project.support.ImmediateTaskTest;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationsViewModelTest extends ImmediateTaskTest {
    @Test public void readWaitsForServerAndDoesNotMutatePreviousList() {
        Repository repository = new Repository();
        NotificationsViewModel model = new NotificationsViewModel(repository);
        NotificationDto notification = new NotificationDto();
        notification.id = 1;
        model.markRead(notification);
        model.markRead(notification);
        assertEquals(1, repository.requests);
        assertFalse(notification.isRead);
        repository.pending.onResult(Resource.success(null));
        assertEquals(1, repository.loads);
        assertFalse(notification.isRead);
    }

    @Test public void failedReadDoesNotMarkNotificationAndCanBeRetried() {
        Repository repository = new Repository();
        NotificationsViewModel model = new NotificationsViewModel(repository);
        NotificationDto notification = new NotificationDto();
        notification.id = 1;
        model.markRead(notification);
        repository.pending.onResult(Resource.error("offline", 0));
        assertFalse(notification.isRead);
        assertEquals(0, repository.loads);
        assertNotNull(model.message().getValue());
        model.markRead(notification);
        assertEquals(2, repository.requests);
    }

    private static final class Repository extends NotificationRepository {
        int requests, loads;
        ApiCalls.Result<Void> pending;
        Repository() { super(null, null, null); }
        @Override public void markRead(long id, ApiCalls.Result<Void> result) { requests++; pending = result; }
        @Override public void page(int page, boolean unread, ApiCalls.Result<NotificationListResponse> result) { loads++; }
    }
}
