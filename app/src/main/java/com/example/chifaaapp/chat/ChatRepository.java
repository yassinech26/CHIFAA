package com.example.chifaaapp.chat;

import androidx.annotation.Keep;
import java.util.List;
import java.util.UUID;
import retrofit2.Call;
import retrofit2.http.*;

/** Contrat Retrofit M5 et projection M4 utilisée pour les cartes de référence. */
public interface ChatRepository {
    int PAGE = 50;
    @GET("api/chat/sessions") Call<List<Session>> sessions(@Query("offset") int offset, @Query("limit") int limit);
    @POST("api/chat/sessions") Call<Session> create();
    @GET("api/chat/sessions/{id}/messages") Call<List<Message>> history(@Path("id") UUID session,
            @Query("offset") int offset, @Query("limit") int limit);
    @POST("api/chat/sessions/{id}/messages") Call<Turn> send(@Path("id") UUID session,@Body Question question);
    @POST("api/chat/messages/{id}/feedback") Call<Void> feedback(@Path("id") UUID message,@Body Feedback feedback);

    @Keep class Session { UUID sessionId; String startedAt, endedAt; }
    @Keep class Message {
        UUID messageId, sessionId;
        String senderRole, messageTimestamp, text, replyKind, contentId;
        Integer userFeedback;
    }
    @Keep class Turn { Message question, answer; }
    @Keep class Question {
        final UUID messageId;
        final String text;
        Question(String text) { this.text = text; messageId = UUID.randomUUID(); }
    }
    @Keep class Feedback { final int rating; Feedback(int rating) { this.rating = rating; } }
}
