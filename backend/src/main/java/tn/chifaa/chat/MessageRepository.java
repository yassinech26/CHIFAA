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
class MessageRepository {
    private final JdbcClient db;
    MessageRepository(JdbcClient db) { this.db = db; }

    // Une réponse archivée ne republie pas le texte d'une fiche retirée ou expirée.
    private static final String READ = """
            select m.message_id, m.session_id, m.sender_role, m.message_timestamp, m.detected_language,
                   m.distress_flag, m.kb_version, m.response_latency_ms, m.user_feedback,
                   case when m.reply_kind='Knowledge' and v.content_id is null then null else m.message_text end as body,
                   case when m.reply_kind='Knowledge' and v.content_id is null then 'ReferenceUnavailable' else m.reply_kind end as kind,
                   m.topic_tag, v.content_id
            from messages m left join kb_visible_items v on v.content_id=m.content_id
            """;
    private static final RowMapper<Message> MESSAGE = (r,n) -> new Message(
            r.getObject("message_id",UUID.class), r.getObject("session_id",UUID.class), r.getString("sender_role"),
            r.getObject("message_timestamp",OffsetDateTime.class).toInstant(), r.getString("body"),
            r.getString("detected_language"), r.getString("topic_tag"), r.getBoolean("distress_flag"),
            r.getString("kind"), r.getString("content_id"), r.getString("kb_version"),
            r.getObject("response_latency_ms",Integer.class), r.getObject("user_feedback",Integer.class));

    List<Message> history(UUID session, int offset, int limit) {
        return db.sql(READ + "where m.session_id=:id order by m.message_timestamp, m.sender_role desc, m.message_id limit :lim offset :off")
                .param("id",session).param("lim",limit).param("off",offset).query(MESSAGE).list();
    }

    Turn turn(UUID session, UUID requestId) {
        var query = db.sql(READ + "where m.session_id=:session and m.message_id=:id")
                .param("session",session).param("id",requestId).query(MESSAGE).optional();
        if (query.isEmpty() || !query.get().senderRole().equals("User")) throw new ResponseStatusException(CONFLICT);
        Message answer = db.sql(READ + "where m.session_id=:session and m.reply_to=:id")
                .param("session",session).param("id",requestId).query(MESSAGE).single();
        return new Turn(query.get(),answer);
    }

    boolean question(UUID session, UUID id, String text, String language, String version) {
        return db.sql("""
                insert into messages(message_id,session_id,sender_role,message_text,detected_language,kb_version)
                values (:id,:session,'User',:text,:lang,:version) on conflict(message_id) do nothing
                """).param("id",id).param("session",session).param("text",text)
                .param("lang",language).param("version",version).update() == 1;
    }

    void answer(UUID session, UUID requestId, ChatService.Reply reply, String language, String version, int latency) {
        boolean distress = reply.kind().equals("Distress");
        db.sql("update messages set distress_flag=:flag,topic_tag=:topic where message_id=:id")
                .param("id",requestId).param("flag",distress).param("topic",reply.topic()).update();
        db.sql("""
                insert into messages(message_id,session_id,reply_to,sender_role,message_text,detected_language,
                    topic_tag,distress_flag,reply_kind,content_id,kb_version,response_latency_ms)
                values (:id,:session,:request,'AI',:text,:lang,:topic,:flag,:kind,:content,:version,:latency)
                """).param("id",UUID.randomUUID()).param("session",session).param("request",requestId)
                .param("text",reply.text()).param("lang",language).param("topic",reply.topic()).param("flag",distress)
                .param("kind",reply.kind()).param("content",reply.contentId()).param("version",version)
                .param("latency",latency).update();
    }

    void rate(UUID user, UUID id, int rating) {
        int count = db.sql("""
                update messages m set user_feedback=:rating from chat_sessions s
                where m.session_id=s.session_id and s.user_pid=:user and m.message_id=:id and m.sender_role='AI'
                """).param("rating",rating).param("user",user).param("id",id).update();
        if (count == 0) throw new ResponseStatusException(NOT_FOUND);
    }
}
