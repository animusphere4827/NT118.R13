package com.example.nt118_project.common;

import org.junit.Test;
import java.time.Instant;
import java.util.TimeZone;

import static org.junit.Assert.*;

public final class ServerTimeTest {
    @Test public void acceptsDatabasePrecisionAndOffsets() {
        long expected = Instant.parse("2026-11-07T03:04:05.123Z").toEpochMilli();
        for (String timestamp : new String[]{
                "2026-11-07T03:04:05.123Z",
                "2026-11-07T03:04:05.123456+00:00",
                "2026-11-07T10:04:05.123456+07:00",
                "2026-11-07T08:34:05.123456+0530",
                "2026-11-06T23:34:05.123456-03:30",
                "2026-11-07 03:04:05.123456+00"}) {
            assertNotNull(timestamp, ServerTime.parse(timestamp));
            assertEquals(timestamp, expected, ServerTime.parse(timestamp).getTime());
        }
    }

    @Test public void acceptsWholeSecondsAndShortFractions() {
        assertEquals(Instant.parse("2026-11-07T03:04:05Z").toEpochMilli(),
                ServerTime.parse("2026-11-07T03:04:05+00:00").getTime());
        assertEquals(Instant.parse("2026-11-07T03:04:05.100Z").toEpochMilli(),
                ServerTime.parse("2026-11-07T03:04:05.1Z").getTime());
    }

    @Test public void parsingIsIndependentOfDeviceTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            long expected = Instant.parse("2026-11-06T20:00:00Z").toEpochMilli();
            for (String zone : new String[]{"Asia/Ho_Chi_Minh", "UTC", "America/New_York"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals(expected, ServerTime.parse("2026-11-07T03:00:00+07:00").getTime());
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test public void formatsUtcAsVietnamLocalTimeAcrossMidnight() {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        assertEquals("08/11/2026 03:04", format.format(ServerTime.parse("2026-11-07T20:04:05.123456+00:00")));
    }

    @Test public void rejectsInvalidDatesAndTrailingText() {
        for (String timestamp : new String[]{null, "", "2026-02-30T03:04:05Z",
                "2026-11-07T03:04:05", "2026-11-07T03:04:05Zextra",
                "2026-11-07T03:04:05+25:00"}) {
            assertNull(timestamp, ServerTime.parse(timestamp));
        }
    }
}
