package com.example.nt118_project.common;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerTime {
    private static final Pattern ISO = Pattern.compile(
        "^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}(?::?\\d{2})?)$");

    private ServerTime() { }

    public static Date parse(String timestamp) {
        if (timestamp == null) return null;
        Matcher match = ISO.matcher(timestamp);
        if (!match.matches()) return null;

        String fraction = match.group(3);
        String millis = ((fraction == null ? "" : fraction) + "000").substring(0, 3);
        String offset = match.group(4).replace(":", "");
        if (offset.equals("Z")) offset = "+0000";
        else if (offset.length() == 3) offset += "00";
        String normalized = match.group(1) + "T" + match.group(2) + "." + millis + offset;

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(normalized, position);
        return position.getIndex() == normalized.length() ? parsed : null;
    }
}
