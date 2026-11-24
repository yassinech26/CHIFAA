package com.chifaa.backend.audit;

import com.chifaa.backend.compatibility.AuditService;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PostgresAuditService implements AuditService {
    private final JdbcClient jdbc;

    public PostgresAuditService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void record(UUID actor, String action, String resource) {
        jdbc.sql("INSERT INTO access_log(actor_id,action,resource_id) VALUES (?,?,?)")
                .params(actor, action, resource).update();
    }
}
