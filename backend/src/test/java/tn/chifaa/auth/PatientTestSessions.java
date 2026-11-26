package tn.chifaa.auth;

import java.util.UUID;

/** Allows focused API tests to use the same in-memory registry as OTP verification. */
public final class PatientTestSessions {
    private PatientTestSessions() {}
    public static UUID resolve(Sessions sessions, String token) { return sessions.resolve(token); }
    public static String bearer(Sessions sessions, UUID pid) {
        return "Bearer " + sessions.open(pid).token();
    }
}
