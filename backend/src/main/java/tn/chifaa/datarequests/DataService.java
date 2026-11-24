package tn.chifaa.datarequests;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.chifaa.users.*;
import tn.chifaa.consents.ConsentService;
import tn.chifaa.health.HealthService;
import tn.chifaa.auth.Sessions;

@Service
@Transactional
public class DataService {
    public record Request(String requestId) {}
    private final JdbcTemplate db;
    private final PatientAccess access;
    private final ProfileService profiles;
    private final ConsentService consents;
    private final HealthService health;
    private final Sessions sessions;
    private final tn.chifaa.auth.PhoneVault vault;
    private final PatientAudit audit;
    private final List<PersonalExportSection> sections;
    public DataService(JdbcTemplate db, PatientAccess access, ProfileService profiles, ConsentService consents,
        HealthService health, Sessions sessions, tn.chifaa.auth.PhoneVault vault, PatientAudit audit, List<PersonalExportSection> sections) {
        this.vault=vault; this.audit=audit; this.sections=List.copyOf(sections);
        this.db=db; this.access=access; this.profiles=profiles; this.consents=consents; this.health=health; this.sessions=sessions;
    }
    public Map<String,Object> export(UUID pid) {
        var result = new LinkedHashMap<String,Object>();
        result.put("userPid",pid);
        result.put("profile",profiles.get(pid));
        result.put("account",db.queryForMap("select access_channel,age_confirmed_18plus,registration_date,last_active_date from users where user_pid=?",pid));
        result.put("phone",vault.decrypt(db.queryForObject("select phone_number_enc from identities where user_pid=?",byte[].class,pid)));
        audit.record(pid,"patient_data","Export","Personal export");
        var permissions = consents.list(pid);
        result.put("consents",permissions);
        boolean healthAllowed=permissions.stream().anyMatch(c->"Health data".equals(c.purpose()) && "Given".equals(c.status()));
        if (healthAllowed) {
            result.put("healthProfile",health.profile(pid));
            result.put("checkins",health.history(pid,null));
        }
        result.put("requests",db.queryForList("select request_id,record_type,received_at,closed_at,outcome from data_requests where user_pid=? order by request_id",pid));
        for (var section:sections) {
            if (section.requiresHealthConsent() && !healthAllowed) continue;
            if (result.containsKey(section.name())) throw new IllegalStateException("Duplicate export section");
            result.put(section.name(),section.read(pid));
        }
        return result;
    }
    public Request requestDeletion(UUID pid) {
        access.lock(pid);
        var ids = db.queryForList("select request_id from data_requests where user_pid=? and record_type='Deletion request' and closed_at is null",String.class,pid);
        if (!ids.isEmpty()) return new Request(ids.get(0));
        return new Request(db.queryForObject("insert into data_requests(user_pid,record_type) values (?,'Deletion request') returning request_id",String.class,pid));
    }
    // Called only by E3's authorized staff workflow. No patient-facing execution endpoint.
    public void executeDeletion(String requestId) {
        var ids = db.queryForList("select user_pid from data_requests where request_id=? and record_type='Deletion request' and closed_at is null",UUID.class,requestId);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT);
        UUID pid=ids.get(0);
        // Staff may fulfill an existing request even after service consent was withdrawn.
        db.queryForList("select user_pid from users where user_pid=? for update",UUID.class,pid);
        // Free-text outcomes supplied by staff may contain a direct identifier.
        db.update("update data_requests set outcome=null where user_pid=?",pid);
        int claimed=db.update("update data_requests set closed_at=current_date,outcome='Deleted' where request_id=? and closed_at is null",requestId);
        if (claimed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT);
        db.update("delete from users where user_pid=?",pid);
        sessions.revoke(pid);
    }
}
