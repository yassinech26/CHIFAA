package tn.chifaa.users;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional
public class ProfileService {
    private final PatientAccess access;
    private final Patients patients;
    public ProfileService(PatientAccess access, Patients patients) { this.access=access; this.patients=patients; }
    public Profile get(UUID pid) { access.lock(pid); return Profile.from(patients.findById(pid).orElseThrow()); }
    public Profile edit(UUID pid, Profile body) {
        access.lock(pid);
        body.apply(patients.findById(pid).orElseThrow());
        return body;
    }
}
