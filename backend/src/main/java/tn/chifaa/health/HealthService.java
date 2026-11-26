package tn.chifaa.health;

import java.util.*;
import java.time.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.chifaa.users.PatientAccess;
import static tn.chifaa.health.HealthDtos.*;

@Service
@Transactional
public class HealthService {
    private final PatientAccess access;
    private final JdbcTemplate db;
    private final tn.chifaa.users.PatientAudit audit;
    public HealthService(PatientAccess access, JdbcTemplate db, tn.chifaa.users.PatientAudit audit) {
        this.access=access; this.db=db; this.audit=audit;
    }
    public Catalog catalog(UUID pid) {
        access.healthVersion(pid);
        var phases=db.queryForList("select code from care_phase_catalog order by code",String.class);
        var effects=db.queryForList("select code from side_effect_catalog order by code",String.class);
        // A partly loaded catalog cannot represent all the choices on the form.
        return new Catalog(phases.size()==7 ? phases : List.of(),effects.size()==11 ? effects : List.of());
    }
    public Form profile(UUID pid) {
        access.healthVersion(pid);
        audit.record(pid,"health_profiles","Read","Personal profile");
        List<String> treatments = db.queryForList("select treatment_type from health_profile_treatments where user_pid=? order by treatment_type",String.class,pid);
        return db.query("select * from health_profiles where user_pid=?",(r,n)->new Form(r.getString("cancer_type"),
            r.getString("care_phase"),r.getString("diagnosis_period_band"),r.getObject("diagnosis_year",Integer.class),
            r.getString("facility_type"),r.getObject("fertility_info_interest",Boolean.class),treatments),pid)
            .stream().findFirst().orElse(new Form(null,null,null,null,null,null,List.of()));
    }
    public Form save(UUID pid, Form f) {
        String version = access.healthVersion(pid);
        audit.record(pid,"health_profiles","Edit","Personal profile");
        if ((f.diagnosisYear()!=null && f.diagnosisYear()>Year.now(ZoneOffset.UTC).getValue())
            || (f.carePhase()!=null && !catalog(pid).carePhases().contains(f.carePhase()))
            || (f.treatments().contains("Prefer not to say") && f.treatments().size()>1)
            || new HashSet<>(f.treatments()).size()!=f.treatments().size()) throw invalid();
        db.update("""
            insert into health_profiles(user_pid,cancer_type,care_phase,diagnosis_period_band,diagnosis_year,facility_type,
              fertility_info_interest,health_consent_version) values (?,?,?,?,?,?,?,?)
            on conflict (user_pid) do update set cancer_type=excluded.cancer_type,care_phase=excluded.care_phase,
              diagnosis_period_band=excluded.diagnosis_period_band,diagnosis_year=excluded.diagnosis_year,
              facility_type=excluded.facility_type,fertility_info_interest=excluded.fertility_info_interest,
              health_consent_version=excluded.health_consent_version,updated_at=now()
            """,pid,f.cancerType(),f.carePhase(),f.diagnosisPeriodBand(),f.diagnosisYear(),f.facilityType(),f.fertilityInfoInterest(),version);
        db.update("delete from health_profile_treatments where user_pid=?",pid);
        for (String t : f.treatments()) db.update("insert into health_profile_treatments values (?,?)",pid,t);
        return f;
    }
    public Checkin add(UUID pid,CheckinInput body) {
        var available = catalog(pid).sideEffects();
        if (available.isEmpty()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        audit.record(pid,"health_checkins","Edit","Personal checkin");
        if (!available.containsAll(body.effects()) || new HashSet<>(body.effects()).size()!=body.effects().size()) throw invalid();
        var id = UUID.randomUUID();
        db.update("insert into health_checkins(checkin_id,user_pid,checkin_date) values (?,?,?)",id,pid,body.date());
        for (String e : body.effects()) db.update("insert into checkin_side_effects values (?,?)",id,e);
        return new Checkin(id,body.date(),body.effects());
    }
    public List<Checkin> history(UUID pid, UUID id) {
        access.healthVersion(pid);
        audit.record(pid,"health_checkins","Read","Personal history");
        String query = """
            select c.checkin_id,c.checkin_date,coalesce(array_agg(e.side_effect order by e.side_effect)
                filter (where e.side_effect is not null),'{}') as effects
            from health_checkins c left join checkin_side_effects e using(checkin_id) where c.user_pid=?
            """;
        Object[] args = {pid};
        if (id!=null) { query += " and c.checkin_id=?"; args = new Object[]{pid,id}; }
        query += " group by c.checkin_id order by c.checkin_date desc,c.checkin_id";
        return db.query(query,(r,n)->new Checkin(r.getObject(1,UUID.class),r.getObject(2,LocalDate.class),
            Arrays.asList((String[])r.getArray(3).getArray())),args);
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST); }
}
