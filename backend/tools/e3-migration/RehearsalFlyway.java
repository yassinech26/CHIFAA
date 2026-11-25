package com.chifaa.backend.compatibility;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Exécute les migrations depuis les deux commits extraits et le provisionnement original d'E3. */
public class RehearsalFlyway {
    public static void main(String[] args) throws Exception {
        String schema = args[0];
        var db = new PGSimpleDataSource();
        db.setURL("jdbc:postgresql://127.0.0.1:" + System.getenv("PGPORT") + "/" + args[1]);
        db.setUser(System.getenv("PGUSER"));
        db.setPassword(System.getenv("PGPASSWORD"));
        var placeholders = new HashMap<String, String>();
        for (int i = 1; i <= 5; i++) placeholders.put("at044Source" + i, "TEST_SOURCE_" + i);
        for (int i = 1; i <= 7; i++) placeholders.put("at022Type" + i, "TEST_TRIGGER_" + i);
        var flyway = Flyway.configure().dataSource(db).schemas(schema).defaultSchema(schema)
                .locations("filesystem:" + Path.of(args[2]).toAbsolutePath())
                .baselineOnMigrate(schema.equals("e2")).baselineVersion("0").placeholders(placeholders).load();
        flyway.migrate();
        flyway.validate();
        if (schema.equals("public")) {
            byte[] secret = new byte[24];
            new SecureRandom().nextBytes(secret);
            new DemoAccounts().provisionAccounts(JdbcClient.create(db),new BCryptPasswordEncoder(),
                    Base64.getEncoder().encodeToString(secret)).run(null);
        }
    }
}
