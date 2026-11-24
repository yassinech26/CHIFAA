package com.chifaa.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Profile("dev")
@RestController
@RequestMapping("/api/auth")
public class LocalAuthentication {
    public record Identity(UUID id, String role) {}
    public record Credentials(@NotBlank @Size(max=100) String username, @NotBlank @Size(max=72) String password, boolean staff) {}
    public record Login(String token, String role, Instant expiresAt) {}
    private record Account(UUID id, String role, String passwordHash) {}
    private record Ticket(Identity identity, Instant expiresAt) {}

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String missingAccountHash;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Ticket> tokens = new ConcurrentHashMap<>();
    private long loginWindow;
    private int loginAttempts;

    public LocalAuthentication(JdbcClient jdbc, PasswordEncoder encoder, Clock clock) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.clock = clock;
        this.missingAccountHash = encoder.encode(UUID.randomUUID().toString());
    }

    @PostMapping("/login")
    synchronized Login login(@Valid @RequestBody Credentials request) {
        long minute = clock.instant().getEpochSecond() / 60;
        if (minute != loginWindow) {
            loginWindow = minute;
            loginAttempts = 0;
        }
        if (++loginAttempts > 60) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        String sql = request.staff()
                ? "SELECT staff_id AS id, role, password_hash FROM staff WHERE username = ?"
                : "SELECT user_pid AS id, 'USER' AS role, password_hash FROM users WHERE username = ?";
        var account = jdbc.sql(sql).param(request.username()).query(Account.class).optional();
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !encoder.matches(request.password(), account.map(Account::passwordHash).orElse(missingAccountHash))
                || account.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Instant now = clock.instant();
        tokens.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        if (tokens.size() >= 1000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        var authenticated = account.orElseThrow();
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plusSeconds(1800);
        tokens.put(token, new Ticket(new Identity(authenticated.id(), authenticated.role()), expiresAt));
        return new Login(token, authenticated.role(), expiresAt);
    }

    public Identity resolve(String token) {
        Ticket ticket = tokens.get(token);
        if (ticket == null) return null;
        if (ticket.expiresAt().isAfter(clock.instant())) return ticket.identity();
        tokens.remove(token, ticket);
        return null;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader("Authorization") String authorization) {
        if (authorization.startsWith("Bearer ")) tokens.remove(authorization.substring(7));
    }
}
