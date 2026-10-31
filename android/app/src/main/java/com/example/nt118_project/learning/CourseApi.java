package com.example.nt118_project.learning;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface CourseApi {
    @GET("learn/path")
    Call<CoursePathDto> path();

    @POST("learn/path/lessons/{id}/vocabulary/start")
    Call<VocabularySession> start(@Path("id") long id, @Body Map<String, Object> body);

    @PUT("learn/path/lessons/{id}/vocabulary/study")
    Call<VocabularySession> study(@Path("id") long id, @Body Map<String, List<Long>> body);

    @POST("learn/path/lessons/{id}/vocabulary/checks")
    Call<VocabularySession.Check> check(@Path("id") long id, @Body Map<String, Object> body);

    @GET("learn/vocabulary-checks/{id}")
    Call<VocabularySession.Check> savedCheck(@Path("id") String id);

    @GET("learn/path/lessons/{id}/vocabulary/review")
    Call<VocabularySession.WordReview> review(@Path("id") long id, @retrofit2.http.Query("page") int page);

    @POST("learn/vocabulary-checks/{id}/submit")
    Call<VocabularySession.Result> submit(@Path("id") String id,
            @Body Map<String, List<VocabularySession.Answer>> body);
}
