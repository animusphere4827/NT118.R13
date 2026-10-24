package com.example.nt118_project.ui.review;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.example.nt118_project.App;
import com.example.nt118_project.data.remote.dto.CardDto;

/** Khóa argument dùng chung giữa các màn bộ thẻ, sửa thẻ và lật thẻ. */
public final class ReviewArgs {

    public static final String DECK_ID = "deck_id";
    public static final String DECK_NAME = "deck_name";
    public static final String CARD_JSON = "card_json";

    private ReviewArgs() {
    }

    public static Bundle deck(long deckId, String deckName) {
        Bundle b = new Bundle();
        b.putLong(DECK_ID, deckId);
        b.putString(DECK_NAME, deckName);
        return b;
    }

    /** card = null khi thêm thẻ mới. */
    public static Bundle card(long deckId, String deckName, @Nullable CardDto card) {
        Bundle b = deck(deckId, deckName);
        if (card != null) {
            b.putString(CARD_JSON, App.container().gson.toJson(card));
        }
        return b;
    }

    @Nullable
    public static CardDto card(Bundle args) {
        String json = args.getString(CARD_JSON);
        return json == null ? null : App.container().gson.fromJson(json, CardDto.class);
    }
}
