package com.example.nt118_project.data.remote.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Thẻ flashcard kèm các trường SM-2 (GET /learn/decks/{id}/cards). */
public class CardDto {
    public long id;
    @SerializedName("deck_id")
    public long deckId;
    @SerializedName("vocabulary_id")
    public Long vocabularyId;
    @SerializedName("front_text")
    public String frontText;
    @SerializedName("back_text")
    public String backText;
    public String ipa;
    @SerializedName("example_sentence")
    public String exampleSentence;
    public String note;
    @SerializedName("image_url")
    public String imageUrl;
    @SerializedName("is_pinned")
    public boolean isPinned;
    @SerializedName("ease_factor")
    public double easeFactor;
    @SerializedName("interval_days")
    public int intervalDays;
    public int repetitions;
    @SerializedName("due_at")
    public String dueAt;
    @SerializedName("last_reviewed_at")
    public String lastReviewedAt;

    /** Ôn đúng liên tiếp từ 3 lần trở lên thì coi là đã thuộc. */
    public static final int MASTERED_REPETITIONS = 3;

    public boolean isNew() {
        return repetitions == 0 && lastReviewedAt == null;
    }

    public boolean isMastered() {
        return repetitions >= MASTERED_REPETITIONS;
    }

    public boolean isDue() {
        Date due = parse(dueAt);
        return due == null || !due.after(new Date());
    }

    /** Server trả thời gian ISO 8601 UTC, ví dụ 2026-10-24T13:55:00.123Z. */
    @Nullable
    public static Date parse(@Nullable String iso) {
        if (iso == null) {
            return null;
        }
        for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX"}) {
            SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                return f.parse(iso);
            } catch (ParseException ignored) {
                // thử mẫu tiếp theo
            }
        }
        return null;
    }

    /** Dữ liệu gửi khi sửa thẻ: server yêu cầu gửi đủ các trường. */
    public CardRequest toRequest() {
        CardRequest r = new CardRequest();
        r.frontText = frontText;
        r.backText = backText;
        r.ipa = ipa == null ? "" : ipa;
        r.exampleSentence = exampleSentence == null ? "" : exampleSentence;
        r.note = note == null ? "" : note;
        r.imageUrl = imageUrl;
        r.isPinned = isPinned;
        return r;
    }
}
