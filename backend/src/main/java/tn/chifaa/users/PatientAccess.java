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
        if (!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from consents where user_pid=? and consent_purpose='Service use' and consent_status='Given')",Boolean.class,pid)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    public String healthVersion(UUID pid) {
        lock(pid);
        return db.queryForList("select consent_version from consents where user_pid=? and consent_purpose='Health data' and consent_status='Given'",String.class,pid)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
}
