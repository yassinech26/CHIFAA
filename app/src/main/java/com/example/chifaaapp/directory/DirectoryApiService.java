package com.example.chifaaapp.directory;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.*;

public interface DirectoryApiService {
    @GET("api/organisations") Call<JsonObject> directory(@Query("page") int page);
    @GET("api/organisations/{id}") Call<JsonObject> organisation(@Path("id") String id);
}
