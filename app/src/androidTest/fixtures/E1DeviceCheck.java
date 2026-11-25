package tn.chifaa;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Deux comptes éphémères. Seul le lanceur peut modifier le consentement. */
public final class E1DeviceCheck extends DeviceCheck {
    private E1DeviceCheck(String adb, String serial) { super(adb, serial); }

    private UUID account(JdbcTemplate db) {
        UUID id = UUID.randomUUID();
        db.update("insert into users(user_pid) values (?)", id);
        db.update("""
                insert into consents(consent_id,user_pid,consent_purpose,consent_status,given_at)
                values (concat('T',nextval('test_consent_seq')),?,'Health data','Given',now())
                """, id);
        return id;
    }

    private void phase(Map<String,String> fixture, String method) throws Exception {
        instrument(fixture, "cache/e1-device-fixture.json", "e1Live", "chat.E1JourneyTest#" + method);
    }

    @Override protected void exercise(JdbcTemplate db, String url, String certificate) throws Exception {
        new ResourceDatabasePopulator(new ClassPathResource("knowledge-api-fixtures.sql"))
                .execute(Objects.requireNonNull(db.getDataSource()));
        UUID a = account(db), b = account(db);
        Map<String,String> fixture = new HashMap<>(Map.of("url", url, "certificate", certificate,
                "a", a.toString(), "b", b.toString()));
        fixture.put("expired", bearer(a.toString(), "chifaa-test", Instant.now().minusSeconds(180)).substring(7));
        Instant expiresAt = Instant.now().plusSeconds(1200);
        fixture.put("a1", bearer(a.toString(), "chifaa-test", expiresAt).substring(7));
        fixture.put("a2", bearer(a.toString(), "chifaa-test", expiresAt).substring(7));
        fixture.put("b1", bearer(b.toString(), "chifaa-test", expiresAt).substring(7));
        phase(fixture, "refreshAndSwitchAccounts");

        UUID session = db.queryForObject("select session_id from chat_sessions where user_pid=?", UUID.class, a);
        UUID answer = db.queryForObject("select message_id from messages where session_id=? and reply_to is not null",
                UUID.class, session);
        fixture.put("sessionA", session.toString());
        fixture.put("answerA", answer.toString());
        int changes = db.update("""
                update consents set consent_status='Withdrawn',withdrawn_at=now()
                where user_pid=? and consent_purpose='Health data' and consent_status='Given'
                """, a);
        if (changes != 1) throw new AssertionError("Un retrait de consentement attendu");
        phase(fixture, "withdrawHealthConsent");

        int sessions = db.queryForObject("select count(*) from chat_sessions", Integer.class);
        int messagesA = db.queryForObject("select count(*) from messages where session_id=?", Integer.class, session);
        int messagesB = db.queryForObject("""
                select count(*) from messages join chat_sessions using(session_id) where user_pid=?
                """, Integer.class, b);
        Integer rating = db.queryForObject("select user_feedback from messages where message_id=?", Integer.class, answer);
        if (sessions != 2 || messagesA != 2 || messagesB != 4 || rating != null)
            throw new AssertionError("État inattendu des conversations après le retrait");
        System.out.println("PostgreSQL OK: 2 sessions, 2 messages A, 4 messages B, note A intacte.");
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("chifaa.chat.kb-version", "KB-20261124");
        System.setProperty("chifaa.chat.triggers[0].phrase", "signal test");
        System.setProperty("chifaa.chat.triggers[0].type", "Test 1");
        System.setProperty("chifaa.chat.triggers[0].severity", "High");
        new E1DeviceCheck(args[0], args[1]).run();
    }
}
