package tn.chifaa.users;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.*;

@Component
public class PatientAccess {
    private final JdbcTemplate db;
    public PatientAccess(JdbcTemplate db) { this.db = db; }
    // All patient operations and consent changes serialize on this row until transaction commit.
    @Transactional(propagation=Propagation.MANDATORY)
    public void lock(UUID pid) {
        int rows = db.update("update users set last_active_date=current_date where user_pid=?",pid);
        if (rows == 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        activeVersion(pid, "Service use");
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public String healthVersion(UUID pid) {
        lock(pid);
        return activeVersion(pid, "Health data");
    }

    private String activeVersion(UUID pid, String purpose) {
        return db.queryForList("""
                select consent_version from (
                    select consent_version, consent_status, withdrawn_at from consents
                    where user_pid=? and consent_purpose=?
                    order by given_at desc, consent_id desc limit 1
                ) latest where consent_status='Given' and withdrawn_at is null
                """, String.class, pid, purpose).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
}
