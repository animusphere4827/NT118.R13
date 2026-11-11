package com.example.nt118_project.ui.learning;

import androidx.annotation.ArrayRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.nt118_project.R;

/** Lời chào ở Trang chủ: chọn theo giờ trên máy và gọi người dùng bằng tên (từ cuối của họ tên). */
public final class Greeting {

    public enum Period {
        MORNING(R.string.greet_morning, R.array.greet_morning_hints),
        NOON(R.string.greet_noon, R.array.greet_noon_hints),
        AFTERNOON(R.string.greet_afternoon, R.array.greet_afternoon_hints),
        EVENING(R.string.greet_evening, R.array.greet_evening_hints),
        NIGHT(R.string.greet_night, R.array.greet_night_hints);

        @StringRes public final int title;
        @ArrayRes public final int hints;

        Period(@StringRes int title, @ArrayRes int hints) {
            this.title = title;
            this.hints = hints;
        }
    }

    private Greeting() { }

    /** 5-10h sáng, 11-12h trưa, 13-17h chiều, 18-21h tối, còn lại là khuya. */
    public static Period period(int hourOfDay) {
        if (hourOfDay >= 5 && hourOfDay < 11) return Period.MORNING;
        if (hourOfDay >= 11 && hourOfDay < 13) return Period.NOON;
        if (hourOfDay >= 13 && hourOfDay < 18) return Period.AFTERNOON;
        if (hourOfDay >= 18 && hourOfDay < 22) return Period.EVENING;
        return Period.NIGHT;
    }

    /** "Ngô Kim Thành" thành "Thành"; tên rỗng trả null để chỉ hiện lời chào. */
    @Nullable
    public static String shortName(@Nullable String fullName) {
        if (fullName == null) return null;
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) return null;
        String[] parts = trimmed.split("\\s+");
        return parts[parts.length - 1];
    }

    /** Chỉ số câu gợi ý: cố định trong một ngày, đổi qua ngày khác để đỡ nhàm. */
    public static int hintIndex(int dayOfYear, int count) {
        return count <= 0 ? 0 : Math.floorMod(dayOfYear, count);
    }
}
