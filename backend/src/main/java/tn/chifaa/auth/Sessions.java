package tn.chifaa.auth;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Component
public class Sessions {
    private record Entry(UUID pid, Instant end) {}
    private final java.time.Clock clock;
    public Sessions(java.time.Clock clock) { this.clock=clock; }
    private final Map<String,Entry> entries = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    synchronized AuthDtos.Session open(UUID pid) {
        Instant now = clock.instant();
        entries.values().removeIf(e -> !e.end.isAfter(now));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant end = now.plusSeconds(1800);
        entries.put(token,new Entry(pid,end));
        return new AuthDtos.Session(token,end,pid);
    }
    synchronized UUID resolve(String token) {
        Entry e = entries.get(token);
        if (e == null) return null;
        if (e.end.isAfter(clock.instant())) return e.pid;
        entries.remove(token);
        return null;
    }
    synchronized void close(String token) { entries.remove(token); }
    public synchronized void revoke(UUID pid) { entries.values().removeIf(e -> e.pid.equals(pid)); }
}
