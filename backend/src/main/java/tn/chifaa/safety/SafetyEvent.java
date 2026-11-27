package tn.chifaa.safety;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

record SafetyEvent(UUID eventId, UUID userPid, UUID sessionId, String triggerType, String severity,
                   String status, String reviewerId, String actionTaken, String referralOutcome, String orgId,
                   Instant flaggedAt, Instant takenInChargeAt, Instant resolvedAt) {

    public Long getHandlingDelaySeconds() { return secondsUntil(takenInChargeAt); }

    public Long getResolutionDelaySeconds() { return secondsUntil(resolvedAt); }

    private Long secondsUntil(Instant end) {
        return end == null ? null : Duration.between(flaggedAt, end).getSeconds();
    }
}
