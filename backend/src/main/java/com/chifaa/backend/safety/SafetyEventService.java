package com.chifaa.backend.safety;

import com.chifaa.backend.compatibility.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SafetyEventService {
    public record Reviewer(UUID id, String username) {}
    public record Statistics(long open, long critical, long resolved) {}
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "Open", Set.of("In progress", "Resolved"),
        "In progress", Set.of("Open", "Resolved"),
        "Resolved", Set.of("In progress", "Closed"),
        "Closed", Set.of());
    public record Update(UUID reviewerId, @Pattern(regexp="ORG-[0-9]{3}") String orgId,
        @Size(max=2000) String actionTaken, @Size(max=2000) String referralOutcome,
        @Pattern(regexp="Open|In progress|Resolved|Closed") String status) {}
    private final SafetyEventRepository events;
    private final StaffIdentityProvider staff;
    private final AuditService audit;

    public SafetyEventService(SafetyEventRepository events, StaffIdentityProvider staff, AuditService audit) {
        this.events = events;
        this.staff = staff;
        this.audit = audit;
    }

    public void flag(UUID user, UUID session, DistressDetectionService.Trigger trigger) { events.insert(user,session,trigger); }

    @Transactional
    public List<SafetyEvent> list(String severity, String status, int page) {
        if (page < 0 || page > 1000 || !Set.of("", "Low", "Medium", "High", "Critical").contains(severity)
                || !Set.of("", "Open", "In progress", "Resolved", "Closed").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        audit.record(staff.requireStaff(),"SAFETY_LIST","queue");
        return events.list(severity,status,page);
    }

    @Transactional
    public SafetyEvent get(UUID id) {
        var event = events.get(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        audit.record(staff.requireStaff(),"SAFETY_READ",id.toString());
        return event;
    }

    @Transactional
    public SafetyEvent update(UUID id, Update changes) {
        UUID actor = staff.requireStaff();
        String current = events.lockStatus(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (changes.status() != null && !current.equals(changes.status()) && !TRANSITIONS.get(current).contains(changes.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid safety event status transition");
        }
        if (changes.reviewerId() != null && !events.eligibleReviewer(changes.reviewerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var event = events.update(id,changes).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        audit.record(actor,"SAFETY_EDIT",id.toString());
        return event;
    }

    List<Reviewer> reviewers() { staff.requireStaff(); return events.reviewers(); }

    @Transactional
    public Statistics statistics() {
        audit.record(staff.requireStaff(), "SAFETY_STATS", "queue");
        return events.statistics();
    }
}
