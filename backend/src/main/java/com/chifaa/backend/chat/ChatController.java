package com.chifaa.backend.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    public record ChatRequest(@NotNull UUID requestId, @NotBlank @Size(max=2000) String text) {}
    public record FeedbackRequest(@NotNull @Min(1) @Max(5) Integer rating) {}
    private final ChatSessionService sessions;
    private final ChatService chat;
    public ChatController(ChatSessionService sessions, ChatService chat) { this.sessions = sessions; this.chat = chat; }

    @PostMapping("/sessions") @ResponseStatus(HttpStatus.CREATED)
    ChatSessionRepository.Session create() { return sessions.create(); }

    @GetMapping("/sessions")
    List<ChatSessionRepository.Session> sessions(@RequestParam(defaultValue="0") int page) { return sessions.list(page); }

    @GetMapping("/sessions/{id}/messages")
    List<ChatService.Entry> messages(@PathVariable UUID id, @RequestParam(defaultValue="0") int page) {
        return chat.history(id,page);
    }

    @PostMapping("/sessions/{id}/messages")
    ChatService.Answer send(@PathVariable UUID id, @Valid @RequestBody ChatRequest request) {
        return chat.send(id,request.requestId(),request.text());
    }

    @PostMapping("/messages/{id}/feedback") @ResponseStatus(HttpStatus.NO_CONTENT)
    void rate(@PathVariable UUID id, @Valid @RequestBody FeedbackRequest feedback) { chat.rate(id,feedback.rating()); }
}
