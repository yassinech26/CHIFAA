package tn.chifaa.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static tn.chifaa.chat.ChatModels.*;

@RestController
@RequestMapping("/api/chat")
class ChatController {
    private final ChatService chat;
    ChatController(ChatService chat) { this.chat = chat; }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    Session create(@AuthenticationPrincipal UUID user) { return chat.create(user); }

    @GetMapping("/sessions")
    List<Session> sessions(@AuthenticationPrincipal UUID user, @RequestParam(defaultValue="0") @Min(0) int offset,
                           @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return chat.list(user,offset,limit);
    }

    @GetMapping("/sessions/{id}/messages")
    List<Message> history(@AuthenticationPrincipal UUID user, @PathVariable UUID id,
                          @RequestParam(defaultValue="0") @Min(0) int offset,
                          @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return chat.history(user,id,offset,limit);
    }

    @PostMapping("/sessions/{id}/messages")
    Turn ask(@AuthenticationPrincipal UUID user, @PathVariable UUID id, @RequestBody @Valid Question question) {
        return chat.ask(user,id,question);
    }

    @PostMapping("/messages/{id}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rate(@AuthenticationPrincipal UUID user, @PathVariable UUID id, @RequestBody @Valid Feedback feedback) {
        chat.rate(user,id,feedback.rating());
    }
}
