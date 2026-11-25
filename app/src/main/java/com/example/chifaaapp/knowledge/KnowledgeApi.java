package com.example.chifaaapp.knowledge;

import androidx.annotation.Keep;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface KnowledgeApi {
    @GET("api/kb")
    Call<Page> list(@Query("q") String keyword, @Query("topic") String topic,
                    @Query("language") String language, @Query("after") String after);

    @GET("api/kb/{id}")
    Call<Item> detail(@Path("id") String id);

    @Keep
    class Page {
        List<Item> items;
        String nextAfter;
    }

    @Keep
    class Item {
        public String title() { return title; }
        public String sourceReference() { return sourceReference; }
        String contentId, title, topic, language, notice;
        Boolean fictional;
        String contentText, sourceType, sourceReference, evidenceLevel, localApplicability;
        String contentVersion, reviewDate, expiryDate;

        boolean validSummary() {
            return contentId != null && contentId.matches("KB-[0-9]{4}") && title != null
                    && topic != null && language != null && fictional != null;
        }

        boolean readable(String today) {
            return validSummary() && contentText != null && sourceReference != null && sourceType != null
                    && reviewDate != null && reviewDate.compareTo(today) <= 0
                    && (expiryDate == null || expiryDate.compareTo(today) > 0);
        }
    }
}
