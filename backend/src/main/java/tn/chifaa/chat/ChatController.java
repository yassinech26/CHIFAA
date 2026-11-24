package tn.chifaa.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static tn.chifaa.chat.ChatModels.*;

@RestController
@RequestMapping("/api/chat")
class ChatController {
    private final ChatService chat;
    ChatController(ChatService chat) { this.chat = chat; }

    private UUID user(Jwt jwt) {
        String sub = jwt.getSubject();
        try {
            UUID id = UUID.fromString(sub == null ? "" : sub);
            if (id.toString().equalsIgnoreCase(sub)) return id;
        } catch (IllegalArgumentException ignored) { }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    Session create(@AuthenticationPrincipal Jwt jwt) { return chat.create(user(jwt)); }

    @GetMapping("/sessions")
    List<Session> sessions(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="0") @Min(0) int offset,
                           @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return chat.list(user(jwt),offset,limit);
    }

    @GetMapping("/sessions/{id}/messages")
    List<Message> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                          @RequestParam(defaultValue="0") @Min(0) int offset,
                          @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return chat.history(user(jwt),id,offset,limit);
    }

    @PostMapping("/sessions/{id}/messages")
    Turn ask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody @Valid Question question) {
        return chat.ask(user(jwt),id,question);
    }

    @PostMapping("/messages/{id}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody @Valid Feedback feedback) {
        chat.rate(user(jwt),id,feedback.rating());
    }
}
