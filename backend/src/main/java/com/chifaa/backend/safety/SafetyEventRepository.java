package com.chifaa.backend.safety;

import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SafetyEventRepository {
    private static final String COLUMNS = """
        event_id AS id,user_pid,session_id,reviewer_id,org_id,trigger_type,severity,flagged_at,status,
        action_taken,referral_outcome,resolved_at,
        CAST(EXTRACT(EPOCH FROM (resolved_at - flagged_at)) AS bigint) AS resolution_seconds
        """;
    private final JdbcClient jdbc;
    public SafetyEventRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    void insert(UUID user, UUID session, DistressDetectionService.Trigger trigger) {
        jdbc.sql("INSERT INTO safety_events(event_id,user_pid,session_id,trigger_type,severity) VALUES (?,?,?,?,?)")
                .params(UUID.randomUUID(),user,session,trigger.type(),trigger.severity()).update();
    }

    List<SafetyEvent> list(String severity, String status, int page) {
        return jdbc.sql("SELECT " + COLUMNS + """
             FROM safety_events WHERE (?='' OR severity=?) AND (?='' OR status=?)
             ORDER BY flagged_at DESC,event_id LIMIT 50 OFFSET ?
             """).params(severity,severity,status,status,page*50).query(SafetyEvent.class).list();
    }

    Optional<SafetyEvent> get(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM safety_events WHERE event_id=?").param(id).query(SafetyEvent.class).optional();
    }

    Optional<String> lockStatus(UUID id) {
        return jdbc.sql("SELECT status FROM safety_events WHERE event_id=? FOR UPDATE").param(id)
                .query(String.class).optional();
    }

    SafetyEventService.Statistics statistics() {
        return jdbc.sql("""
            SELECT count(*) FILTER (WHERE status='Open') AS open,
                   count(*) FILTER (WHERE severity='Critical') AS critical,
                   count(*) FILTER (WHERE status='Resolved') AS resolved
            FROM safety_events
            """).query(SafetyEventService.Statistics.class).single();
    }

    Optional<SafetyEvent> update(UUID id, SafetyEventService.Update update) {
        return jdbc.sql("""
             UPDATE safety_events SET reviewer_id=COALESCE(?,reviewer_id),org_id=COALESCE(?,org_id),
               action_taken=COALESCE(?,action_taken),referral_outcome=COALESCE(?,referral_outcome),
               status=COALESCE(?,status),resolved_at=CASE
                 WHEN COALESCE(?,status) IN ('Resolved','Closed') THEN COALESCE(resolved_at,clock_timestamp()) ELSE NULL END
             WHERE event_id=? RETURNING
             """ + COLUMNS).params(Arrays.asList(update.reviewerId(),update.orgId(),update.actionTaken(),update.referralOutcome(),
                update.status(),update.status(),id)).query(SafetyEvent.class).optional();
    }

    boolean eligibleReviewer(UUID id) {
        return jdbc.sql("SELECT staff_id FROM staff WHERE staff_id=? AND role IN ('SAFETY_REVIEWER','ADMIN')")
                .param(id).query(UUID.class).optional().isPresent();
    }

    List<SafetyEventService.Reviewer> reviewers() {
        return jdbc.sql("SELECT staff_id AS id,username FROM staff WHERE role IN ('SAFETY_REVIEWER','ADMIN') ORDER BY username")
                .query(SafetyEventService.Reviewer.class).list();
    }
}
