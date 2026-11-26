package tn.chifaa;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import tn.chifaa.auth.*;
import static org.mockito.Mockito.*;
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

    @TestConfiguration(proxyBeanMethods=false)
    static class OtpFixture {
        @Bean @Primary Clock testClock() {
            Clock clock = mock(Clock.class);
            when(clock.instant()).thenReturn(Instant.now());
            return clock;
        }
        @Bean @Primary Inbox inbox() { return new Inbox(); }
    }

    static class Inbox implements OtpDelivery {
        final Map<UUID,String> pending = new ConcurrentHashMap<>();
        public void send(UUID challenge, String code) { pending.put(challenge, code); }
    }

    @Override protected Set<Class<?>> extraSources() { return Set.of(OtpFixture.class); }

    private final ObjectMapper json = new ObjectMapper();
    private HttpClient client;
    private URI base;
    private Inbox inbox;

    private void connect(String url, String certificate) throws Exception {
        base = URI.create(url);
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        trust.load(null);
        trust.setCertificateEntry("fixture", CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(certificate.getBytes(StandardCharsets.US_ASCII))));
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trust);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, factory.getTrustManagers(), null);
        client = HttpClient.newBuilder().sslContext(tls).build();
    }

    private JsonNode call(String method, String path, Map<String,?> body, String token, int expected) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != expected)
            throw new AssertionError(method+" "+path+" expected "+expected+", got "+response.statusCode());
        return json.readTree(response.body());
    }

    private String login(String phone) throws Exception {
        String id = call("POST", "api/auth/login", Map.of("phone",phone), null, 200).required("challengeId").asText();
        String code = Objects.requireNonNull(inbox.pending.remove(UUID.fromString(id)));
        return call("POST", "api/auth/verify", Map.of("challengeId",id,"code",code), null, 200).required("token").asText();
    }

    private void consent(String token, String purpose, boolean given) throws Exception {
        call("PUT", "api/consents", Map.of("purpose",purpose,"version","v1.0","given",given), token, 200);
    }

    private void phase(Map<String,String> fixture, String method) throws Exception {
        instrument(fixture, "cache/e1-device-fixture.json", "e1Live", "chat.E1JourneyTest#" + method);
    }

    @Override protected void exercise(ServletWebServerApplicationContext context, JdbcTemplate db, String url, String certificate) throws Exception {
        new ResourceDatabasePopulator(new ClassPathResource("knowledge-api-fixtures.sql"))
                .execute(Objects.requireNonNull(db.getDataSource()));
        connect(url, certificate);
        inbox = context.getBean(Inbox.class);
        Sessions registry = context.getBean(Sessions.class);
        Clock clock = context.getBean(Clock.class);
        String phoneA = "+12025550100", phoneB = "+12025550101";
        String a1 = login(phoneA), b1 = login(phoneB);
        UUID a = PatientTestSessions.resolve(registry, a1), b = PatientTestSessions.resolve(registry, b1);
        if (a.equals(b)) throw new AssertionError("Deux identités distinctes attendues");
        Map<String,String> fixture = new HashMap<>(Map.of("url", url, "certificate", certificate,
                "a", a.toString(), "b", b.toString(), "a1", a1, "b1", b1));
        phase(fixture, "initialConsents");
        UUID session = db.queryForObject("select session_id from chat_sessions where user_pid=?", UUID.class, a);
        UUID answer = db.queryForObject("select message_id from messages where session_id=? and reply_to is not null",
                UUID.class, session);
        fixture.put("sessionA", session.toString());
        fixture.put("answerA", answer.toString());
        Instant later = clock.instant().plus(Duration.ofMinutes(31));
        when(clock.instant()).thenReturn(later);
        call("GET", "api/kb", null, a1, 401);
        call("GET", "api/chat/sessions", null, b1, 401);
        String a2 = login(phoneA), b2 = login(phoneB);
        if (a1.equals(a2) || !a.equals(PatientTestSessions.resolve(registry, a2))
                || !b.equals(PatientTestSessions.resolve(registry, b2))) throw new AssertionError("Reconnexion OTP");
        fixture.put("a2", a2);
        fixture.put("b2", b2);
        consent(b2, "Health data", true);
        phase(fixture, "reconnectAndSwitchAccounts");
        consent(a2, "Health data", false);
        phase(fixture, "withdrawHealthConsent");
        consent(a2, "Service use", false);
        call("GET", "api/kb", null, a2, 401);
        call("GET", "api/chat/sessions", null, a2, 401);
        call("GET", "api/chat/sessions", null, b2, 200);

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
        System.setProperty("spring.profiles.active", "demo-seed");
        System.setProperty("chifaa.chat.kb-version", "KB-20261124");
        System.setProperty("chifaa.chat.triggers[0].phrase", "signal test");
        System.setProperty("chifaa.chat.triggers[0].type", "Test 1");
        System.setProperty("chifaa.chat.triggers[0].severity", "High");
        new E1DeviceCheck(args[0], args[1]).run();
    }
}
