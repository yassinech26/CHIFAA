package com.chifaa.backend.compatibility;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.chifaa.backend.auth.LocalAuthentication.Identity;

@Service
public class LocalServices implements UserIdentityProvider, StaffIdentityProvider, ConsentProvider {
    private final JdbcClient jdbc;
    public LocalServices(JdbcClient jdbc) { this.jdbc = jdbc; }

    private UUID identity(boolean staff) {
        var principal = SecurityContextHolder.getContext().getAuthentication();
        if (principal != null && principal.getPrincipal() instanceof Identity identity
                && staff == !identity.role().equals("USER")) {
            return identity.id();
        }
        throw new AccessDeniedException("Access denied");
    }

    public UUID requireUser() { return identity(false); }
    public UUID requireStaff() { return identity(true); }

    public boolean hasConsent(UUID user) {
        return jdbc.sql("SELECT consent FROM users WHERE user_pid = ?").param(user).query(Boolean.class).optional().orElse(false);
    }

}
