package com.example.nt118_project.learning;

import com.google.gson.JsonObject;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.*;

public interface StudyApi {
    @GET("learn/catalog/{resource}") Call<JsonObject> catalog(@Path("resource") String resource, @Query("page") int page, @Query("limit") int limit, @QueryMap Map<String,String> filters);
    @GET("learn/catalog/{resource}/{id}") Call<JsonObject> content(@Path("resource") String resource, @Path("id") long id);
    @GET("learn/readings") Call<JsonObject> readings(@Query("page") int page, @Query("limit") int limit, @Query("search") String search);
    @GET("learn/readings/{id}") Call<JsonObject> reading(@Path("id") long id, @Query("translation") boolean translation);
    @PUT("learn/readings/{id}/progress") Call<JsonObject> completeReading(@Path("id") long id, @Body JsonObject body);
    @POST("learn/readings/{id}/lookups") Call<JsonObject> lookup(@Path("id") long id, @Body JsonObject body);
    @GET("learn/quizzes") Call<JsonObject> quizzes(@Query("page") int page, @Query("limit") int limit);
    @GET("learn/quizzes/{id}") Call<JsonObject> quiz(@Path("id") long id);
    @GET("learn/{kind}/attempts") Call<JsonObject> history(@Path("kind") String kind, @Query("page") int page, @Query("limit") int limit, @QueryMap Map<String,String> filters);
    @POST("learn/{kind}/{id}/attempts") Call<JsonObject> start(@Path("kind") String kind, @Path("id") long id, @Body JsonObject body);
    @POST("learn/quizzes/{id}/attempts") Call<JsonObject> startQuiz(@Path("id") long id, @Body JsonObject body);
    @GET("learn/{kind}/attempts/{id}") Call<JsonObject> attempt(@Path("kind") String kind, @Path("id") long id);
    @PATCH("learn/{kind}/attempts/{id}/answers") Call<JsonObject> save(@Path("kind") String kind, @Path("id") long id, @Body JsonObject body);
    @POST("learn/quiz/attempts/{id}/answers") Call<JsonObject> checkQuiz(@Path("id") long id, @Body JsonObject body);
    @POST("learn/{kind}/attempts/{id}/submit") Call<JsonObject> submit(@Path("kind") String kind, @Path("id") long id, @Body JsonObject body);
    @GET("learn/grammar/{id}/workspace") Call<JsonObject> grammar(@Path("id") long id);
    @POST("learn/grammar/{id}/check") Call<JsonObject> checkGrammar(@Path("id") long id, @Body JsonObject body);
    @PUT("learn/grammar/{id}/progress") Call<Void> grammarProgress(@Path("id") long id, @Body JsonObject body);
    @GET("learn/analytics") Call<JsonObject> analytics();
    @GET("learn/mistakes") Call<JsonObject> mistakes(@Query("page") int page, @Query("limit") int limit);
    @GET("learn/decks") Call<JsonObject> decks();
    @POST("learn/decks") Call<JsonObject> deck(@Body JsonObject body);
    @POST("learn/decks/{id}/cards") Call<JsonObject> card(@Path("id") long id,@Body JsonObject body);
}
