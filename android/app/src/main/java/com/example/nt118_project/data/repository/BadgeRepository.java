package com.example.nt118_project.data.repository;

import com.example.nt118_project.data.remote.ApiCalls;
import com.example.nt118_project.data.remote.api.BadgeApi;
import com.example.nt118_project.data.remote.dto.BadgeListResponse;
import com.example.nt118_project.data.remote.dto.FeatureBadgeRequest;

/** Bộ sưu tập huy hiệu và huy hiệu gắn trên hồ sơ. */
public interface BadgeRepository {

    void badges(ApiCalls.Result<BadgeListResponse> result);

    void feature(String badgeCode, ApiCalls.Result<Void> result);

    class Remote implements BadgeRepository {
        private final BadgeApi api;
        private final ApiCalls calls;

        public Remote(BadgeApi api, ApiCalls calls) {
            this.api = api;
            this.calls = calls;
        }

        @Override
        public void badges(ApiCalls.Result<BadgeListResponse> result) {
            calls.enqueue(api.badges(), result);
        }

        @Override
        public void feature(String badgeCode, ApiCalls.Result<Void> result) {
            calls.enqueue(api.feature(new FeatureBadgeRequest(badgeCode)), result);
        }
    }
}
