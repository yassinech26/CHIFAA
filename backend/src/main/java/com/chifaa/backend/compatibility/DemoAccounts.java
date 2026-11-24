package com.chifaa.backend.compatibility;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DemoAccounts {
    @Bean
    ApplicationRunner provisionAccounts(JdbcClient jdbc, PasswordEncoder encoder,
            @Value("${CHIFAA_DEMO_PASSWORD:}") String password) {
        return args -> {
            if (password.isBlank()) return;
            if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("CHIFAA_DEMO_PASSWORD must contain at least 12 characters and at most 72 bytes");
            }
            String hash = encoder.encode(password);
            for (String username : new String[]{"demo.user","demo.other"}) {
                jdbc.sql("INSERT INTO users(user_pid,username,password_hash,consent) VALUES (?,?,?,true) "
                        + "ON CONFLICT (username) DO UPDATE SET password_hash=EXCLUDED.password_hash")
                    .params(id(username),username,hash).update();
            }
            for (String role : new String[]{"SAFETY_REVIEWER","CONTENT_TEAM","ADMIN"}) {
                String username = "demo." + role.toLowerCase(java.util.Locale.ROOT);
                jdbc.sql("INSERT INTO staff(staff_id,username,password_hash,role) VALUES (?,?,?,?) "
                        + "ON CONFLICT (username) DO UPDATE SET password_hash=EXCLUDED.password_hash")
                    .params(id(username),username,hash,role).update();
            }
        };
    }

    private UUID id(String username) { return UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)); }
}
