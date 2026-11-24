package com.chifaa.backend.chat;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MessageRepository {
    public record Message(UUID id, UUID sessionId, UUID contentId, String senderRole, Instant messageTimestamp,
            String messageText, String detectedLanguage, String topicTag, boolean distressFlag, boolean rephraseFlag,
            Long responseLatencyMs, Integer userFeedback) {}
    private static final String COLUMNS = """
        message_id AS id,session_id,content_id,sender_role,message_timestamp,message_text,
        detected_language,topic_tag,distress_flag,rephrase_flag,response_latency_ms,user_feedback
        """;
    private final JdbcClient jdbc;
    public MessageRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    Optional<Message> retry(UUID session, UUID request, String role) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM messages WHERE session_id=? AND request_id=? AND sender_role=?")
                .params(session, request, role).query(Message.class).optional();
    }

    long recentRequests(UUID session) {
        return jdbc.sql("""
            SELECT count(*) FROM messages WHERE session_id=? AND sender_role='User'
              AND message_timestamp > now() - interval '1 minute'
            """).param(session).query(Long.class).single();
    }

    Message insert(UUID session, UUID request, String role, String text, String language, String topic,
            boolean distress, boolean rephrase, Long latency, UUID content, Integer version) {
        return jdbc.sql("""
            INSERT INTO messages(message_id,session_id,request_id,sender_role,message_text,detected_language,topic_tag,
                distress_flag,rephrase_flag,response_latency_ms,content_id,kb_version,message_timestamp)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,clock_timestamp()) RETURNING
            """ + COLUMNS).params(Arrays.asList(UUID.randomUUID(),session,request,role,text,language,topic,
                distress,rephrase,latency,content,version)).query(Message.class).single();
    }

    List<Message> list(UUID session, int offset) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM (SELECT * FROM messages WHERE session_id=? "
                    + "ORDER BY message_timestamp DESC,message_id DESC LIMIT 100 OFFSET ?) page ORDER BY message_timestamp,message_id")
                .params(session,offset).query(Message.class).list();
    }

    boolean rate(UUID id, UUID user, int rating) {
        return jdbc.sql("""
            UPDATE messages m SET user_feedback=? FROM chat_sessions s
            WHERE m.message_id=? AND m.session_id=s.session_id AND s.user_pid=? AND m.sender_role='AI'
            """).params(rating,id,user).update() == 1;
    }
}
