package tn.chifaa.auth;

import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.chifaa.users.*;

@Service
public class Accounts {
    private final Patients patients;
    private final JdbcTemplate db;
    private final PatientAccess access;
    public Accounts(Patients patients, JdbcTemplate db, PatientAccess access) {
        this.patients = patients;
        this.db = db;
        this.access = access;
    }

    @Transactional
    public UUID authenticate(OtpService.Ticket ticket) {
        var ids = db.queryForList("select user_pid from identities where phone_hash=?",UUID.class,ticket.fingerprint());
        var today = LocalDate.now(ZoneOffset.UTC);
        if (ticket.registration() == null) {
            if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            var pid = ids.get(0);
            access.lock(pid);
            return pid;
        }
        if (!ids.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var r = ticket.registration();
        Patient p = new Patient();
        p.userPid = UUID.randomUUID();
        r.profile().apply(p);
        p.ageConfirmed18plus = true;
        p.registrationDate = today;
        p.lastActiveDate = today;
        p.accessChannel = "App";
        patients.saveAndFlush(p);
        db.update("insert into identities(user_pid,phone_number_enc,phone_hash) values (?,?,?)",
            p.userPid,r.encryptedPhone(),ticket.fingerprint());
        grant(p.userPid,"Service use",r.version());
        if (r.health()) grant(p.userPid,"Health data",r.version());
        if (r.survey()) grant(p.userPid,"Survey",r.version());
        return p.userPid;
    }
    private void grant(UUID pid,String purpose,String version) {
        db.update("insert into consents(user_pid,consent_purpose,consent_version,consent_status,capture_method) values (?,?,?,'Given','In-app')",pid,purpose,version);
    }
}
