package tn.chifaa.chat;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;
import static org.springframework.http.HttpStatus.*;
import static tn.chifaa.chat.ChatModels.*;

@Repository
class ChatRepository {
    private final JdbcClient db;
    ChatRepository(JdbcClient db) { this.db = db; }

    private static final RowMapper<Session> SESSION = (r,n) -> {
        var end = r.getObject("ended_at", OffsetDateTime.class);
        return new Session(r.getObject("session_id", UUID.class), r.getString("channel"),
                r.getObject("started_at", OffsetDateTime.class).toInstant(), end == null ? null : end.toInstant());
    };

    void checkConsent(UUID user) {
        boolean allowed = db.sql("""
                select consent_status = 'Given' and withdrawn_at is null from consents
                where user_pid = :user and consent_purpose = 'Health data' and given_at <= current_timestamp
                order by given_at desc, consent_id desc limit 1
                """).param("user",user).query(Boolean.class).optional().orElse(false);
        if (!allowed) throw new ResponseStatusException(FORBIDDEN);
    }

    Session create(UUID user) {
        return db.sql("""
                insert into chat_sessions(session_id,user_pid,channel) values (:id,:user,'App') returning *
                """).param("id",UUID.randomUUID()).param("user",user).query(SESSION).single();
    }

    List<Session> sessions(UUID user, int offset, int limit) {
        return db.sql("""
                select * from chat_sessions where user_pid=:user
                order by started_at desc, session_id limit :limit offset :offset
                """).param("user",user).param("limit",limit).param("offset",offset).query(SESSION).list();
    }

    Session owned(UUID user, UUID session, boolean lock) {
        return db.sql("select * from chat_sessions where user_pid=:user and session_id=:id" + (lock ? " for update" : ""))
                .param("user",user).param("id",session).query(SESSION).optional()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    void alert(UUID user, UUID session, ChatPolicy.Trigger trigger) {
        db.sql("""
                insert into safety_events(event_id,user_pid,session_id,trigger_type,severity)
                values (:id,:user,:session,:type,:severity)
                """).param("id",UUID.randomUUID()).param("user",user).param("session",session)
                .param("type",trigger.type()).param("severity",trigger.severity().name()).update();
    }
}
