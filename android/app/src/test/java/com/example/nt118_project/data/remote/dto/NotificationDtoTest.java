package com.example.nt118_project.data.remote.dto;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationDtoTest {
    private NotificationDto notification() {
        return new Gson().fromJson("{\"id\":1,\"type\":\"SYSTEM\",\"title\":\"Notice\",\"data\":{\"screen\":\"badges\"}}", NotificationDto.class);
    }

    @Test public void identicalNotificationsHaveEqualContentAndHash() {
        NotificationDto first = notification();
        NotificationDto second = notification();
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test public void changesToIconDestinationTimeOrReadStateRequireRebind() {
        NotificationDto first = notification();
        NotificationDto second = notification();
        second.type = "ACHIEVEMENT";
        assertNotEquals(first, second);
        second = notification();
        second.data.addProperty("screen", "mock_result");
        assertNotEquals(first, second);
        second = notification();
        second.createdAt = "2026-11-07T00:30:00+07:00";
        assertNotEquals(first, second);
        second = notification();
        second.isRead = true;
        assertNotEquals(first, second);
    }
}
