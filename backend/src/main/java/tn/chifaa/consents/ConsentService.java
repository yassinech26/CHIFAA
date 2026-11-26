package tn.chifaa.consents;

import java.util.*;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tn.chifaa.users.PatientAccess;
import tn.chifaa.auth.Sessions;
@Service
@Transactional
public class ConsentService {
    public record Evidence(String id,String purpose,String version,String status,Instant givenAt,Instant withdrawnAt,String method) {}
    private final JdbcTemplate db;
    private final PatientAccess access;
    private final Sessions sessions;
    public ConsentService(JdbcTemplate db, PatientAccess access, Sessions sessions) {
        this.db=db; this.access=access; this.sessions=sessions;
    }
    public List<Evidence> list(UUID pid) {
        access.lock(pid);
        return db.query("select * from consents where user_pid=? order by given_at,consent_id",(rs,n)->{
            var withdrawal = rs.getTimestamp("withdrawn_at");
            return new Evidence(rs.getString("consent_id"),rs.getString("consent_purpose"),rs.getString("consent_version"),
                rs.getString("consent_status"),rs.getTimestamp("given_at").toInstant(),withdrawal==null?null:withdrawal.toInstant(),rs.getString("capture_method"));
        },pid);
    }
    public void change(UUID pid,ConsentController.Change c) {
        access.lock(pid);
        if (c.given()) {
            db.update("insert into consents(user_pid,consent_purpose,consent_version,consent_status,capture_method) values (?, ?, ?, 'Given','In-app') on conflict do nothing",pid,c.purpose(),c.version());
        } else {
            db.update("update consents set consent_status='Withdrawn', withdrawn_at=clock_timestamp() where user_pid=? and consent_purpose=? and consent_status='Given'",pid,c.purpose());
            if ("Service use".equals(c.purpose())) sessions.revoke(pid);
        }
    }
}
