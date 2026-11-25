package com.example.chifaaapp.clinicalreview;

import androidx.annotation.Keep;
import com.google.gson.JsonObject;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

interface ReviewApi {
    @GET("api/admin/kb") Call<List<Summary>> list(@Query("status") String status, @Query("offset") int offset, @Query("limit") int limit);
    @GET("api/admin/kb/{id}/versions") Call<List<Summary>> history(@Path("id") String id, @Query("offset") int offset, @Query("limit") int limit);
    @GET("api/admin/kb/{id}") Call<Item> get(@Path("id") String id);
    @POST("api/admin/kb") Call<Item> create(@Body JsonObject body);
    @PATCH("api/admin/kb/{id}") Call<Item> patch(@Path("id") String id, @Body JsonObject body);

    @Keep class Summary {
        String contentId, title, language, contentVersion, approvalStatus, previousVersionId;
        boolean fictional;
    }
    @Keep class Item extends Summary {
        String topic, contentText, sourceType, sourceReference, evidenceLevel, localApplicability, expiryDate;
        String clinicalReviewer, reviewDate, supersededBy;
    }
}
