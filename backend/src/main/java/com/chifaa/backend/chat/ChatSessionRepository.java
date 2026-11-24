package com.chifaa.backend.chat;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChatSessionRepository {
    public record Session(UUID id, Instant startedAt, Instant endedAt, String preview) {}
    private final JdbcClient jdbc;
    public ChatSessionRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    Session create(UUID user) {
        return jdbc.sql("""
            INSERT INTO chat_sessions(session_id,user_pid) VALUES (?,?)
            RETURNING session_id AS id, started_at, ended_at, CAST(NULL AS text) AS preview
            """)
                .params(UUID.randomUUID(), user).query(Session.class).single();
    }

    boolean owns(UUID session, UUID user, boolean lock) {
        return jdbc.sql("SELECT session_id FROM chat_sessions WHERE session_id=? AND user_pid=?" + (lock ? " FOR UPDATE" : ""))
                .params(session,user).query(UUID.class).optional().isPresent();
    }

    List<Session> list(UUID user, int offset) {
        return jdbc.sql("""
            SELECT session_id AS id,started_at,ended_at,
                (SELECT left(m.message_text,120) FROM messages m WHERE m.session_id=s.session_id
                    ORDER BY m.message_timestamp DESC,m.message_id DESC LIMIT 1) AS preview
            FROM chat_sessions s WHERE user_pid=? ORDER BY started_at DESC,session_id LIMIT 50 OFFSET ?
            """)
                .params(user,offset).query(Session.class).list();
    }
}
