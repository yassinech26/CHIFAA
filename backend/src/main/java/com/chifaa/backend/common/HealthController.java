package com.chifaa.backend.common;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class HealthController {
    private final JdbcClient jdbc;
    public HealthController(JdbcClient jdbc) { this.jdbc = jdbc; }
    @GetMapping("/api/health")
    Map<String,String> health() {
        jdbc.sql("SELECT 1").query(Integer.class).single();
        return Map.of("status", "up");
    }
}
