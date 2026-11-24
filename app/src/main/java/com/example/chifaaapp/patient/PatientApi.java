package com.example.chifaaapp.patient;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;
import okhttp3.ResponseBody;
import static com.example.chifaaapp.patient.Models.*;
interface PatientApi {
    @POST("auth/register") Call<Challenge> register(@Body Register body);
    @POST("auth/login") Call<Challenge> login(@Body Login body);
    @POST("auth/verify") Call<Session> verify(@Body Verify body);
    @POST("auth/logout") Call<Void> logout();
    @GET("users/me") Call<Profile> profile();
    @PUT("users/me") Call<Profile> edit(@Body Profile body);
    @GET("consents") Call<List<Evidence>> consents();
    @PUT("consents") Call<Void> change(@Body Change body);
    @GET("health/catalog") Call<Catalog> catalog();
    @GET("health/profile") Call<Health> health();
    @PUT("health/profile") Call<Health> save(@Body Health body);
    @GET("health/checkins") Call<List<Checkin>> history();
    @POST("health/checkins") Call<Checkin> create(@Body Checkin body);
    @GET("health/checkins/{id}") Call<Checkin> one(@Path("id") String id);
    @GET("health/fertility") Call<List<Item>> items(@Query("language") String language);
    @GET("data/export") Call<ResponseBody> export(@Query("format") String format);
    @POST("data/deletion") Call<Request> deletion();
}
