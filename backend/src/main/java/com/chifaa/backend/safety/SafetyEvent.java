package com.chifaa.backend.safety;
import java.time.Instant;
import java.util.UUID;
public record SafetyEvent(UUID id, UUID userPid, UUID sessionId, UUID reviewerId, String orgId,
    String triggerType, String severity, Instant flaggedAt, String status, String actionTaken,
    String referralOutcome, Instant resolvedAt, Long resolutionSeconds) {}
