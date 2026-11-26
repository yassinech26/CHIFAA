package tn.chifaa.auth;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tn.chifaa.users.Profile;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
public class OtpService {
    record Registration(byte[] encryptedPhone, Profile profile, boolean health, boolean survey, String version) {}
    record Ticket(String fingerprint, Registration registration) {}
    private static final class Pending {
        final Ticket ticket;
        final String code;
        final Instant expires;
        int remaining = 5;
        Pending(Ticket ticket, String code, Instant expires) {
            this.ticket = ticket; this.code = code; this.expires = expires;
        }
    }
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<String, Instant> cooldown = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final OtpDelivery delivery;
    private final PhoneVault vault;
    private final Clock clock;
    public OtpService(OtpDelivery delivery, PhoneVault vault,Clock clock) {
        this.delivery=delivery; this.vault=vault; this.clock=clock;
    }

    public AuthDtos.Challenge register(AuthDtos.Register r) {
        var data = new Registration(vault.encrypt(r.phone()), r.profile(), r.health(), r.survey(), r.version());
        return issue(new Ticket(vault.fingerprint(r.phone()), data));
    }
    public AuthDtos.Challenge login(String phone) { return issue(new Ticket(vault.fingerprint(phone), null)); }

    private synchronized AuthDtos.Challenge issue(Ticket ticket) {
        Instant now = clock.instant();
        pending.values().removeIf(p -> !p.expires.isAfter(now));
        cooldown.values().removeIf(end -> !end.isAfter(now));
        if (cooldown.containsKey(ticket.fingerprint()) || cooldown.size() >= 2000)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        UUID id = UUID.randomUUID();
        String code = String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        Instant end = now.plusSeconds(300);
        delivery.send(id,code);
        pending.put(id,new Pending(ticket,code,end));
        cooldown.put(ticket.fingerprint(),end);
        return new AuthDtos.Challenge(id,end);
    }
    synchronized Ticket consume(AuthDtos.Verify verify) {
        Pending p = pending.get(verify.challengeId());
        if (p == null || !p.expires.isAfter(clock.instant()) || p.remaining <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        p.remaining--;
        if (!p.code.equals(verify.code())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        pending.remove(verify.challengeId());
        cooldown.remove(p.ticket.fingerprint());
        return p.ticket;
    }
}
