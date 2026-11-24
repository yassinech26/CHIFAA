package tn.chifaa.chat;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ChatModels {
    private ChatModels() {}
    public record Session(UUID sessionId, String channel, Instant startedAt, Instant endedAt) {}
    public record Question(@NotBlank @Size(max = 4000) @Pattern(regexp = "[^\\x00]*") String text, UUID messageId) {}
    public record Feedback(@NotNull @Min(1) @Max(5) Integer rating) {}
    public record Message(UUID messageId, UUID sessionId, String senderRole, Instant messageTimestamp,
                          String text, String detectedLanguage, String topicTag, boolean distressFlag,
                          String replyKind, String contentId, String kbVersion, Integer responseLatencyMs,
                          Integer userFeedback) {}
    public record Turn(Message question, Message answer) {}
}
