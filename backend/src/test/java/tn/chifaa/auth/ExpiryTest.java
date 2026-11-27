package tn.chifaa.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpiryTest {
    @Test void tokenExpiresAtItsDeadline() {
        var clock=mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.EPOCH);
        var sessions=new Sessions(clock);
        UUID pid=UUID.randomUUID();
        var session=sessions.open(pid);
        assertEquals(pid,session.userPid());
        assertEquals(pid,sessions.resolve(session.token()));
        when(clock.instant()).thenReturn(session.expiresAt());
        assertNull(sessions.resolve(session.token()));
    }
    @Test void otpExpiresAtItsDeadline() {
        var clock=mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.EPOCH);
        String[] delivered=new String[1];
        var vault=mock(PhoneVault.class);
        when(vault.fingerprint(anyString())).thenReturn("fictional");
        var otp=new OtpService((id,code)->delivered[0]=code,vault,clock);
        var challenge=otp.login("+12025550123");
        when(clock.instant()).thenReturn(challenge.expiresAt());
        assertThrows(ResponseStatusException.class,()->otp.consume(new AuthDtos.Verify(challenge.challengeId(),delivered[0])));
    }
}
