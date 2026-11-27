package tn.chifaa.safety;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import tn.chifaa.PostgresJwtTest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {"chifaa.chat.kb-version=KB-20261124", "chifaa.chat.triggers[0].phrase=signal fictif",
        "chifaa.chat.triggers[0].type=Test 1", "chifaa.chat.triggers[0].severity=Critical"})
class SafetyApiTest extends PostgresJwtTest {
    private static final String ROOT = "/api/admin/safety-events";
    private static final String START = "{\"status\":\"In progress\"}";
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    private UUID id;
    private String token;

    @BeforeEach void queue() throws Exception {
        db.update("delete from safety_events");
        db.update("""
                insert into staff(staff_id,role,active) values
                  ('SR-01','Safety reviewer',true), ('SR-02','Safety reviewer',true),
                  ('SR-OFF','Safety reviewer',false), ('CR-01','Clinical reviewer',true),
                  ('AD-01','Admin',true), ('DT-01','Data steward',true), ('CT-01','Content team',true)
                on conflict (staff_id) do update set role=excluded.role, active=excluded.active
                """);
        db.update("insert into organisations(org_id) values ('ORG-001') on conflict do nothing");
        id = event("High");
        token = bearer("SR-01", "chifaa-test");
    }

    private UUID event(String severity) {
        UUID key = UUID.randomUUID();
        db.update("""
                insert into safety_events(event_id,trigger_type,severity,flagged_at)
                values (?,'Test 1',?,now()-interval '10 minutes')
                """, key, severity);
        return key;
    }

    private ResultActions change(String auth, UUID key, String body) throws Exception {
        return mvc.perform(patch(ROOT + "/" + key).header("Authorization", auth)
                .contentType("application/json").content(body));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode update(String body) throws Exception { return read(change(token, id, body)); }

    @Test void fullCycleCalculatesBothIntervalsAndKeepsFirstTimestamps() throws Exception {
        var open = read(mvc.perform(get(ROOT).header("Authorization", token))).get(0);
        assertEquals("Open", open.get("status").asText());
        assertTrue(open.get("handlingDelaySeconds").isNull());
        assertTrue(open.get("resolutionDelaySeconds").isNull());

        update("{\"reviewerId\":\"SR-02\"}");
        var inProgress = update(START);
        assertEquals("SR-02", inProgress.get("reviewerId").asText());
        Instant flagged = Instant.parse(open.get("flaggedAt").asText());
        Instant handled = Instant.parse(inProgress.get("takenInChargeAt").asText());
        assertEquals(Duration.between(flagged, handled).getSeconds(), inProgress.get("handlingDelaySeconds").longValue());
        assertTrue(inProgress.get("handlingDelaySeconds").longValue() >= 600);
        assertEquals(inProgress, update(START));
        var resolved = update("""
                {"status":"Resolved","actionTaken":"Referral","referralOutcome":"Contacted","orgId":"ORG-001"}
                """);
        Instant end = Instant.parse(resolved.get("resolvedAt").asText());
        assertEquals(Duration.between(flagged, end).getSeconds(), resolved.get("resolutionDelaySeconds").longValue());
        var closed = update("{\"status\":\"Closed\"}");
        for (String field : new String[]{"takenInChargeAt","resolvedAt","handlingDelaySeconds","resolutionDelaySeconds",
                "actionTaken","referralOutcome","orgId","reviewerId"}) assertEquals(resolved.get(field), closed.get(field));
        assertEquals(closed, update("{\"status\":\"Closed\"}"));
        change(token,id,"{\"status\":\"Resolved\"}").andExpect(status().isConflict());
        change(token,id,"{\"orgId\":null}").andExpect(status().isConflict());
        assertEquals(closed, read(mvc.perform(get(ROOT).param("status","Closed").header("Authorization",token))).get(0));
    }

    @ParameterizedTest @ValueSource(strings={"CR-01","AD-01","DT-01","CT-01","SR-OFF","MISSING",
            "ba468f5c-e55b-4277-9695-261b785413a6"})
    void bothRoutesRequireAnActiveSafetyReviewer(String subject) throws Exception {
        String auth = bearer(subject,"chifaa-test");
        mvc.perform(get(ROOT).header("Authorization",auth)).andExpect(status().isForbidden());
        change(auth,id,START).andExpect(status().isForbidden());
        assertEquals("Open", db.queryForObject("select event_status from safety_events where event_id=?",String.class,id));
    }

    @Test void identityAndRevocationAreVerifiedOnEveryRequest() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(patch(ROOT+"/"+id).contentType("application/json").content(START)).andExpect(status().isUnauthorized());
        change(bearer("SR-01","wrong"),id,START).andExpect(status().isUnauthorized());
        update(START);
        db.update("update staff set active=false where staff_id='SR-01'");
        change(token,id,"{\"status\":\"Resolved\"}").andExpect(status().isForbidden());
        mvc.perform(get(ROOT).header("Authorization",token)).andExpect(status().isForbidden());
    }

    @Test void sortsFiltersAndBoundsPages() throws Exception {
        event("Medium");
        event("Low");
        UUID first = event("Critical");
        db.update("update safety_events set flagged_at=flagged_at-interval '1 minute' where event_id=?",first);
        event("Critical");
        read(change(token,id,START));
        var page = read(mvc.perform(get(ROOT).param("status","Open").param("limit","2").header("Authorization",token)));
        assertEquals(2, page.size());
        assertEquals(first.toString(), page.get(0).get("eventId").asText());
        assertEquals("Critical", page.get(1).get("severity").asText());
        var next = read(mvc.perform(get(ROOT).param("status","Open").param("offset","2").header("Authorization",token)));
        assertEquals("Medium", next.get(0).get("severity").asText());
        assertEquals("Low", next.get(1).get("severity").asText());
        for (String query : new String[]{"status=Unknown","limit=101","limit=0","offset=-1"})
            mvc.perform(get(ROOT+"?"+query).header("Authorization",token)).andExpect(status().isBadRequest());
    }

    @Test void invalidPatchesRollbackAndPartialEditsKeepOtherFields() throws Exception {
        change(token,UUID.randomUUID(),START).andExpect(status().isNotFound());
        change(token,id,"{\"status\":\"Resolved\"}").andExpect(status().isConflict());
        for (String body : new String[]{"{}","[]","{\"status\":null}","{\"status\":\"Other\"}",
                "{\"reviewerId\":\"SR-OFF\"}","{\"reviewerId\":\"CR-01\"}","{\"reviewerId\":7}",
                "{\"resolvedAt\":\"2001-01-01T00:00:00Z\"}","{\"actionTaken\":\"unknown\"}",
                "{\"referralOutcome\":\"other\"}","{\"status\":\"In progress\",\"orgId\":\"MISSING\"}"})
            change(token,id,body).andExpect(status().isBadRequest());
        var open = read(mvc.perform(get(ROOT).header("Authorization",token))).get(0);
        assertTrue(open.get("takenInChargeAt").isNull());
        assertEquals("Open", open.get("status").asText());
        var progress = update(START);
        assertEquals("SR-01", progress.get("reviewerId").asText());
        change(token,id,"{\"status\":\"Open\"}").andExpect(status().isConflict());
        update("{\"orgId\":\"ORG-001\",\"actionTaken\":\"Referral\"}");
        var cleared = update("{\"orgId\":null}");
        assertTrue(cleared.get("orgId").isNull());
        assertEquals("Referral", cleared.get("actionTaken").asText());
    }

    @Test void concurrentClaimsHaveOneFirstReviewerAndTimestamp() throws Exception {
        String second = bearer("SR-02","chifaa-test");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> { start.await(); return read(change(token,id,START)); });
            var two = pool.submit(() -> { start.await(); return read(change(second,id,START)); });
            start.countDown();
            assertEquals(one.get(15,TimeUnit.SECONDS), two.get(15,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test void chatSignalIsVisibleToB3WithOnlyTrackingMetadata() throws Exception {
        UUID user = patient(true);
        String mobile = patientBearer(user);
        var created = mvc.perform(post("/api/chat/sessions").header("Authorization",mobile))
                .andExpect(status().isCreated()).andReturn();
        String session = json.readTree(created.getResponse().getContentAsByteArray()).get("sessionId").asText();
        mvc.perform(post("/api/chat/sessions/"+session+"/messages").header("Authorization",mobile)
                .contentType("application/json").content("{\"text\":\"signal fictif\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer.replyKind").value("Distress"));
        var alert = read(mvc.perform(get(ROOT).header("Authorization",token))).get(0);
        assertEquals("Critical",alert.get("severity").asText());
        assertEquals(session,alert.get("sessionId").asText());
        assertEquals(user.toString(),alert.get("userPid").asText());
        assertEquals("Open",alert.get("status").asText());
        assertFalse(alert.toString().contains("signal fictif"));
    }

    @Test void legacyResolutionDoesNotInventAHandlingTime() throws Exception {
        db.update("""
                update safety_events set event_status='Closed', resolved_at=flagged_at+interval '42.75 seconds'
                where event_id=?
                """,id);
        var legacy = read(mvc.perform(get(ROOT).header("Authorization",token))).get(0);
        assertEquals(42, legacy.get("resolutionDelaySeconds").longValue());
        assertTrue(legacy.get("handlingDelaySeconds").isNull());
    }
}
