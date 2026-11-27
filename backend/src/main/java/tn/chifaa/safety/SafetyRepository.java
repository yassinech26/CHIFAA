package tn.chifaa.safety;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Repository
class SafetyRepository {
    private final JdbcClient db;
    SafetyRepository(JdbcClient db) { this.db = db; }

    private static final String COLUMNS = """
            event_id, user_pid, session_id, trigger_type, severity, event_status, reviewer_id,
            action_taken, referral_outcome, org_id, flagged_at, taken_in_charge_at, resolved_at
            """;
    private static final RowMapper<SafetyEvent> EVENT = (row, index) -> new SafetyEvent(
            row.getObject("event_id", UUID.class), row.getObject("user_pid", UUID.class),
            row.getObject("session_id", UUID.class), row.getString("trigger_type"), row.getString("severity"),
            row.getString("event_status"), row.getString("reviewer_id"), row.getString("action_taken"),
            row.getString("referral_outcome"), row.getString("org_id"), time(row, "flagged_at"),
            time(row, "taken_in_charge_at"), time(row, "resolved_at"));

    private static Instant time(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    boolean reviewer(String staffId, boolean lock) {
        String query = "select staff_id from staff where staff_id=:id and active and role='Safety reviewer'";
        return staffId != null && staffId.length() <= 10 && db.sql(query + (lock ? " for share" : ""))
                .param("id", staffId).query(String.class).optional().isPresent();
    }

    List<SafetyEvent> list(String status, int offset, int limit) {
        var statement = db.sql("select " + COLUMNS + " from safety_events "
                + (status == null ? "" : "where event_status=:status ") + """
                order by case severity when 'Critical' then 1 when 'High' then 2 when 'Medium' then 3 else 4 end,
                         flagged_at, event_id
                limit :limit offset :offset
                """).param("limit", limit).param("offset", offset);
        if (status != null) statement = statement.param("status", status);
        return statement.query(EVENT).list();
    }

    SafetyEvent lock(UUID id) {
        return db.sql("select " + COLUMNS + " from safety_events where event_id=:id for update")
                .param("id", id).query(EVENT).optional().orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    SafetyEvent update(UUID id, String status, String reviewer, String action, String outcome, String org) {
        return db.sql("""
                update safety_events set event_status=:status, reviewer_id=:reviewer, action_taken=:action,
                    referral_outcome=:outcome, org_id=:org,
                    taken_in_charge_at=case when event_status='Open' and :status='In progress'
                        then coalesce(taken_in_charge_at,clock_timestamp()) else taken_in_charge_at end,
                    resolved_at=case when event_status='In progress' and :status='Resolved'
                        then coalesce(resolved_at,clock_timestamp()) else resolved_at end
                where event_id=:id returning
                """ + COLUMNS).param("id", id).param("status", status).param("reviewer", reviewer)
                .param("action", action).param("outcome", outcome).param("org", org).query(EVENT).single();
    }
}
