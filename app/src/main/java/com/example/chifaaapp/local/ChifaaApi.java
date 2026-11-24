package com.example.chifaaapp.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.*;

public interface ChifaaApi extends com.example.chifaaapp.directory.DirectoryApiService {
    @POST("api/auth/login") Call<JsonObject> login(@Body JsonObject body);
    @POST("api/auth/logout") Call<Void> logout();
    @GET("api/admin/organisations") Call<JsonObject> adminDirectory(@Query("page") int page, @Query("stale") boolean stale);
    @GET("api/admin/organisations/statistics") Call<JsonObject> directoryStats();
    @GET("api/admin/safety-events/statistics") Call<JsonObject> safetyStats();
    @POST("api/admin/organisations") Call<JsonObject> createOrganisation(@Body JsonObject body);
    @PATCH("api/admin/organisations/{id}") Call<JsonObject> updateOrganisation(@Path("id") String id, @Body JsonObject body);
    @POST("api/admin/organisations/{id}/verify") Call<JsonObject> verify(@Path("id") String id, @Body JsonObject body);
    @GET("api/admin/safety-events") Call<JsonArray> events(@Query("severity") String severity, @Query("status") String status, @Query("page") int page);
    @GET("api/admin/safety-events/reviewers") Call<JsonArray> reviewers();
    @GET("api/admin/safety-events/{id}") Call<JsonObject> event(@Path("id") String id);
    @PATCH("api/admin/safety-events/{id}") Call<JsonObject> updateEvent(@Path("id") String id, @Body JsonObject body);
}
