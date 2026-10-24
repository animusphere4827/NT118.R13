package com.example.nt118_project.data.remote.dto;

/** POST /learn/cards/{id}/review: quality 0 đến 5 theo SM-2, dưới 3 là quên (học lại từ đầu). */
public class ReviewRequest {
    public final int quality;

    public ReviewRequest(int quality) {
        this.quality = quality;
    }
}
