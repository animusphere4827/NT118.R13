package com.example.nt118_project.ui.learning;

import android.content.Context;

import com.example.nt118_project.R;

/** Chuyên mục bài báo RSS (khớp NEWS_CATEGORIES ở server/backend/news.js) và nhãn tiếng Việt. */
public final class NewsCategories {

    public static final String[] FILTERS = {"BUSINESS", "TECHNOLOGY", "WORLD", "SCIENCE", "ENVIRONMENT", "TRAVEL"};

    private NewsCategories() { }

    public static String label(Context context, String category) {
        switch (category == null ? "" : category) {
            case "BUSINESS": return context.getString(R.string.news_cat_business);
            case "TECHNOLOGY": return context.getString(R.string.news_cat_technology);
            case "WORLD": return context.getString(R.string.news_cat_world);
            case "SCIENCE": return context.getString(R.string.news_cat_science);
            case "ENVIRONMENT": return context.getString(R.string.news_cat_environment);
            case "TRAVEL": return context.getString(R.string.news_cat_travel);
            default: return context.getString(R.string.news_screen_title);
        }
    }
}
