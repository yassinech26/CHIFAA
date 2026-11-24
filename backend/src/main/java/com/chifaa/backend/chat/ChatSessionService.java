package com.chifaa.backend.chat;
import com.chifaa.backend.compatibility.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatSessionService {
    private final ChatSessionRepository sessions;
    private final UserIdentityProvider identity;
    private final ConsentProvider consent;

    public ChatSessionService(ChatSessionRepository sessions, UserIdentityProvider identity, ConsentProvider consent) {
        this.sessions = sessions;
        this.identity = identity;
        this.consent = consent;
    }

    UUID user() {
        UUID user = identity.requireUser();
        if (!consent.hasConsent(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }

    ChatSessionRepository.Session create() { return sessions.create(user()); }
    List<ChatSessionRepository.Session> list(int page) { return sessions.list(user(), offset(page, 50)); }

    UUID owned(UUID session, boolean lock) {
        UUID user = user();
        if (!sessions.owns(session, user, lock)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return user;
    }

    static int offset(int page, int size) {
        if (page < 0 || page > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return page * size;
    }
}
