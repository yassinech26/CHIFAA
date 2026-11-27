package com.example.chifaaapp.safety;

import androidx.annotation.Keep;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import retrofit2.http.Query;

interface SafetyRepository {
    int PAGE = 50;

    @GET("api/admin/safety-events")
    Call<List<Event>> list(@Query("status") String status, @Query("offset") int offset, @Query("limit") int limit);

    @PATCH("api/admin/safety-events/{id}")
    Call<Event> update(@Path("id") UUID id, @Body JsonObject changes);

    @Keep class Event {
        UUID eventId, userPid, sessionId;
        String triggerType, severity, status, reviewerId, actionTaken, referralOutcome, orgId;
        String flaggedAt, takenInChargeAt, resolvedAt;
        Long handlingDelaySeconds, resolutionDelaySeconds;
    }
}
