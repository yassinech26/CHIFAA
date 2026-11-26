package tn.chifaa.chat;

import org.springframework.stereotype.Service;
import tn.chifaa.users.PatientAccess;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.springframework.http.HttpStatus.*;
import static tn.chifaa.chat.ChatModels.*;

@Service
@Transactional
class ChatService {
    record Reply(String kind, String text, String contentId, String topic) {}
    private final PatientAccess access;
    private final ChatRepository sessions;
    private final MessageRepository messages;
    private final ChatPolicy policy;
    private final KnowledgeSearchService knowledge;

    ChatService(PatientAccess access, ChatRepository sessions, MessageRepository messages, ChatPolicy policy, KnowledgeSearchService knowledge) {
        this.access = access;
        this.sessions = sessions;
        this.messages = messages;
        this.policy = policy;
        this.knowledge = knowledge;
    }

    Session create(UUID user) {
        access.healthVersion(user);
        return sessions.create(user);
    }

    List<Session> list(UUID user, int offset, int limit) {
        access.healthVersion(user);
        return sessions.sessions(user,offset,limit);
    }

    List<Message> history(UUID user, UUID id, int offset, int limit) {
        access.healthVersion(user);
        sessions.owned(user,id,false);
        return messages.history(id,offset,limit);
    }

    Turn ask(UUID user, UUID sessionId, Question request) {
        long start = System.nanoTime();
        access.healthVersion(user);
        Session session = sessions.owned(user,sessionId,true);
        if (session.endedAt() != null) throw new ResponseStatusException(CONFLICT);
        if (!policy.ready()) throw new ResponseStatusException(SERVICE_UNAVAILABLE);
        String text = request.text().strip();
        UUID id = request.messageId() == null ? UUID.randomUUID() : request.messageId();
        String language = ChatText.language(text);
        if (!messages.question(sessionId,id,text,language,policy.kbVersion())) {
            Turn existing = messages.turn(sessionId,id);
            if (!text.equals(existing.question().text())) throw new ResponseStatusException(CONFLICT);
            return existing;
        }

        boolean arabic = ChatText.arabicReply(text,language);
        ResourceBundle replies = ResourceBundle.getBundle("i18n.chat.replies",
                arabic ? Locale.forLanguageTag("ar") : Locale.FRENCH);
        String normalized = ChatText.normalize(text);
        var trigger = ChatText.distress(normalized,policy);
        Reply reply;
        if (trigger.isPresent()) {
            sessions.alert(user,sessionId,trigger.get());
            String help = policy.resource(arabic);
            String intro = replies.getString("distress");
            reply = new Reply("Distress", help == null || help.isBlank() ? intro : intro + "\n" + help,null,null);
        } else if (ChatText.medical(normalized)) {
            reply = new Reply("MedicalLimit",replies.getString("medicalLimit"),null,null);
        } else {
            reply = knowledge.search(text,language)
                    .map(hit -> new Reply("Knowledge",hit.notice() == null ? hit.content() : replies.getString("fictionalNotice") + "\n" + hit.content(),
                            hit.id(),hit.topic()))
                    .orElseGet(() -> new Reply("Directory",replies.getString("directory"),null,null));
        }
        int latency = (int)Math.min(Integer.MAX_VALUE,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
        messages.answer(sessionId,id,reply,language,policy.kbVersion(),latency);
        return messages.turn(sessionId,id);
    }

    void rate(UUID user, UUID id, int rating) {
        access.healthVersion(user);
        messages.rate(user,id,rating);
    }
}
