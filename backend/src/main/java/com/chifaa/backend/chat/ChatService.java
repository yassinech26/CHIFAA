package com.chifaa.backend.chat;

import com.chifaa.backend.compatibility.KnowledgeSearchProvider;
import com.chifaa.backend.compatibility.KnowledgeSearchProvider.Reference;
import com.chifaa.backend.safety.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {
    public record Entry(MessageRepository.Message message, Reference reference, boolean showDirectory,
                        List<SafetyResourceService.Resource> resources) {}
    public record Answer(MessageRepository.Message userMessage, Entry assistant) {}
    private final ChatSessionService sessions;
    private final MessageRepository messages;
    private final LanguageDetectionService languages;
    private final TopicDetectionService topics;
    private final MedicalBoundaryService boundary;
    private final DistressDetectionService distress;
    private final SafetyEventService safety;
    private final SafetyResourceService resources;
    private final KnowledgeSearchProvider knowledge;
    private final ResponseBuilderService responses;

    public ChatService(ChatSessionService sessions, MessageRepository messages, LanguageDetectionService languages,
            TopicDetectionService topics, MedicalBoundaryService boundary, DistressDetectionService distress,
            SafetyEventService safety, SafetyResourceService resources, KnowledgeSearchProvider knowledge,
            ResponseBuilderService responses) {
        this.sessions = sessions;
        this.messages = messages;
        this.languages = languages;
        this.topics = topics;
        this.boundary = boundary;
        this.distress = distress;
        this.safety = safety;
        this.resources = resources;
        this.knowledge = knowledge;
        this.responses = responses;
    }

    @Transactional
    public Answer send(UUID session, UUID request, String input) {
        long start = System.nanoTime();
        UUID user = sessions.owned(session, true);
        String text = input.strip();
        if (text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var previous = messages.retry(session,request,"AI");
        if (previous.isPresent()) {
            var original = messages.retry(session,request,"User").orElseThrow();
            if (!original.messageText().equals(text)) throw new ResponseStatusException(HttpStatus.CONFLICT);
            return new Answer(original, entry(previous.get()));
        }
        if (messages.recentRequests(session) >= 30) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);

        String language = languages.detect(text);
        String normalized = TopicDetectionService.normalize(text);
        var trigger = distress.detect(normalized);
        boolean blocked = trigger.isEmpty() && boundary.blocks(normalized);
        String topic = trigger.isEmpty() && !blocked ? topics.topic(normalized) : "";
        var saved = messages.insert(session,request,"User",text,language,topic,trigger.isPresent(),false,null,null,null);
        trigger.ifPresent(found -> safety.flag(user,session,found));
        Reference reference = trigger.isPresent() || blocked ? null : knowledge.search(topic,language,normalized).orElse(null);
        String response = responses.build(language,trigger.isPresent(),blocked,reference,
                trigger.isPresent() && !resources.resources(language).isEmpty());
        var reply = messages.insert(session,request,"AI",response,language,topic,trigger.isPresent(),
                reference == null && !blocked && trigger.isEmpty(), (System.nanoTime()-start)/1_000_000,
                reference == null ? null : reference.id(), reference == null ? null : reference.version());
        return new Answer(saved,entry(reply));
    }

    List<Entry> history(UUID session, int page) {
        sessions.owned(session,false);
        return messages.list(session,ChatSessionService.offset(page,100)).stream().map(this::entry).toList();
    }

    void rate(UUID message, int rating) {
        if (!messages.rate(message,sessions.user(),rating)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private Entry entry(MessageRepository.Message message) {
        Reference reference = message.contentId() == null ? null : knowledge.find(message.contentId()).orElse(null);
        return new Entry(message, reference, message.distressFlag() || message.rephraseFlag(),
                message.distressFlag() && "AI".equals(message.senderRole())
                        ? resources.resources(message.detectedLanguage()) : List.of());
    }
}
