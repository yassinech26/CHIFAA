package com.example.chifaaapp.chat;

import com.google.gson.*;
import retrofit2.Call;
import retrofit2.http.*;

public interface ChatApiService {
    @POST("api/chat/sessions") Call<JsonObject> createSession();
    @GET("api/chat/sessions") Call<JsonArray> sessions(@Query("page") int page);
    @GET("api/chat/sessions/{id}/messages") Call<JsonArray> messages(@Path("id") String id, @Query("page") int page);
    @POST("api/chat/sessions/{id}/messages") Call<JsonObject> send(@Path("id") String id, @Body JsonObject body);
    @POST("api/chat/messages/{id}/feedback") Call<Void> rate(@Path("id") String id, @Body JsonObject body);
}
