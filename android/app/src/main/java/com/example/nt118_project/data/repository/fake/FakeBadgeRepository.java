package com.example.nt118_project.data.repository.fake;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.dto.BadgeDto;
import com.example.nt118_project.data.remote.dto.BadgeListResponse;
import com.example.nt118_project.data.repository.BadgeRepository;

/** Dữ liệu mẫu 9 huy hiệu theo thiết kế Figma (3 đã mở khóa). Huy hiệu gắn hồ sơ lưu trong máy. */
public class FakeBadgeRepository implements BadgeRepository {

    private static final String KEY_FEATURED = "fake_featured_badge";
    private static final String[] CODES = {"streak_5", "vocab_50", "email_10", "streak_14", "vocab_100",
            "part7_deep", "score_400", "score_450", "streak_30"};
    private static final String[] ICONS = {"flame", "book_open", "mail", "calendar_check", "book_open",
            "target", "trending_up", "award", "flame"};
    private static final String[] DATES = {"2026-10-08T12:00:00.000Z", "2026-10-03T09:30:00.000Z",
            "2026-09-28T14:10:00.000Z"};
    private static final int[] TARGETS = {5, 50, 10, 14, 100, 10, 400, 450, 30};
    private static final int[] PROGRESS = {5, 50, 10, 5, 62, 4, 360, 360, 5};

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    public FakeBadgeRepository(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
    }

    @Override
    public void badges(ApiCalls.Result<BadgeListResponse> result) {
        String[] names = context.getResources().getStringArray(R.array.fake_badge_names);
        String[] reqs = context.getResources().getStringArray(R.array.fake_badge_requirements);
        String[] descs = context.getResources().getStringArray(R.array.fake_badge_descriptions);
        BadgeListResponse r = new BadgeListResponse();
        for (int i = 0; i < CODES.length; i++) {
            BadgeDto b = new BadgeDto();
            b.code = CODES[i];
            b.name = names[i];
            b.requirement = reqs[i];
            b.description = descs[i];
            b.icon = ICONS[i];
            b.unlocked = i < DATES.length;
            b.unlockedAt = b.unlocked ? DATES[i] : null;
            b.target = TARGETS[i];
            b.progress = PROGRESS[i];
            r.rows.add(b);
        }
        r.total = CODES.length;
        r.unlockedCount = DATES.length;
        r.featuredCode = prefs.getString(KEY_FEATURED, null);
        main.postDelayed(() -> result.onResult(Resource.success(r)), 400);
    }

    @Override
    public void feature(String badgeCode, ApiCalls.Result<Void> result) {
        prefs.edit().putString(KEY_FEATURED, badgeCode).apply();
        main.postDelayed(() -> result.onResult(Resource.success(null)), 400);
    }
}
