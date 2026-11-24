package com.chifaa.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named="CHIFAA_RUN_PG_TESTS", matches="true")
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean Clock authClock;
    @Value("${CHIFAA_DEMO_PASSWORD}") String password;

    @Test
    void expiresTokenAtDeadline() throws Exception {
        Instant start = Instant.now();
        org.mockito.Mockito.doReturn(start).when(authClock).instant();
        String token = login("demo.user",false);
        execute(get("/api/chat/sessions"),token,null,200);
        org.mockito.Mockito.doReturn(start.plusSeconds(1800)).when(authClock).instant();
        execute(get("/api/chat/sessions"),token,null,401);
    }

    @Test
    void chatOwnershipRetryRatingAndStaffWorkflow() throws Exception {
        String user = login("demo.user",false);
        String other = login("demo.other",false);
        String reviewer = login("demo.safety_reviewer",true);
        String session = execute(post("/api/chat/sessions"),user,null,201).get("id").asText();
        String messages = "/api/chat/sessions/" + session + "/messages";
        execute(get(messages),other,null,404);
        execute(post(messages), user, Map.of("requestId",UUID.randomUUID(),"text"," "),400);
        var request = Map.of("requestId",UUID.randomUUID(),"text","Scénario détresse démo");
        var reply = execute(post(messages),user,request,200);
        assertEquals(reply,execute(post(messages),user,request,200));
        assertTrue(reply.at("/assistant/message/distressFlag").asBoolean());
        assertTrue(reply.at("/assistant/message/responseLatencyMs").asLong() >= 0);
        assertEquals(2,execute(get(messages),user,null,200).size());
        assertTrue(execute(get("/api/chat/sessions"),user,null,200).get(0).get("preview").asText().contains("personne de confiance"));
        var feedback = "/api/chat/messages/" + reply.at("/assistant/message/id").asText() + "/feedback";
        execute(post(feedback),user,Map.of("rating",5),204);
        execute(post(feedback),user,Map.of("rating",6),400);
        execute(post(feedback),other,Map.of("rating",5),404);

        String events = "/api/admin/safety-events";
        execute(get(events),user,null,403);
        var event = execute(get(events).param("severity","High").param("status","Open"),reviewer,null,200);
        String eventId = null;
        for (JsonNode entry : event) if (entry.get("sessionId").asText().equals(session)) eventId = entry.get("id").asText();
        assertNotNull(eventId);
        var reviewers = execute(get(events+"/reviewers"),reviewer,null,200);
        execute(get(events+"/statistics"),user,null,403);
        assertTrue(execute(get(events+"/statistics"),reviewer,null,200).get("open").asLong() >= 1);
        execute(patch(events+"/"+eventId),reviewer,Map.of("status","Closed"),409);
        var changed = execute(patch(events+"/"+eventId),reviewer,
                Map.of("reviewerId",reviewers.get(0).get("id").asText(),"status","Resolved","actionTaken","Exercice"),200);
        assertEquals("Resolved",changed.get("status").asText());
        assertTrue(changed.get("resolutionSeconds").asLong() >= 0);
        execute(patch(events+"/"+eventId),reviewer,Map.of("status","Closed"),200);
        execute(patch(events+"/"+eventId),reviewer,Map.of("status","Open"),409);
        assertTrue(jdbc.sql("SELECT count(*) FROM access_log WHERE resource_id=?").param(eventId).query(Long.class).single() > 0);
    }

    @Test
    void answersOnlyFromEligibleKnowledgeAndRefusesMedicalRequests() throws Exception {
        String user = login("demo.user",false);
        String session = execute(post("/api/chat/sessions"),user,null,201).get("id").asText();
        String messages = "/api/chat/sessions/" + session + "/messages";
        UUID approved = UUID.randomUUID();
        insertReference(approved,"Approved",90,"Transport démo");
        insertReference(UUID.randomUUID(),"Approved",-1,"Expirée");
        insertReference(UUID.randomUUID(),"Draft",90,"Brouillon");
        insertReference(UUID.randomUUID(),"Retired",90,"Retirée");
        insertReference(UUID.randomUUID(),"Replaced",90,"Remplacée");
        var found = send(messages,user,"Transport");
        assertEquals(approved.toString(),found.at("/assistant/reference/id").asText());
        assertTrue(found.at("/assistant/message/messageText").asText().contains("Contenu fictif"));
        jdbc.sql("UPDATE kb_items SET status='Retired' WHERE content_id=?").param(approved).update();
        assertTrue(send(messages,user,"Transport").at("/assistant/showDirectory").asBoolean());
        assertTrue(send(messages,user,"Bonjour ailleurs").at("/assistant/showDirectory").asBoolean());
        for (String query : List.of("Établir un diagnostic","Quelle posologie pour le transport ?")) {
            var refusal = send(messages,user,query);
            assertTrue(refusal.at("/assistant/reference").isNull());
            assertTrue(refusal.at("/assistant/message/messageText").asText().contains("équipe soignante"));
        }
        assertFalse(execute(get("/api/chat/sessions"),user,null,200).isEmpty());
        execute(post("/api/auth/logout"),user,null,204);
        execute(get(messages),user,null,401);
    }

    @Test
    void postgresPreservesSafetyEventWhenAccountIsDeleted() {
        UUID user = UUID.randomUUID(), session = UUID.randomUUID(), event = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(user_pid,username,password_hash) VALUES (?,?,?)")
                .params(user,user.toString(),"test-only").update();
        jdbc.sql("INSERT INTO chat_sessions(session_id,user_pid) VALUES (?,?)").params(session,user).update();
        jdbc.sql("INSERT INTO safety_events(event_id,user_pid,session_id,trigger_type,severity) VALUES (?,?,?,?,?)")
                .params(event,user,session,"DEMO_DISTRESS","Low").update();
        jdbc.sql("DELETE FROM users WHERE user_pid=?").param(user).update();
        assertTrue(jdbc.sql("SELECT user_pid IS NULL AND session_id IS NULL FROM safety_events WHERE event_id=?")
                .param(event).query(Boolean.class).single());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                jdbc.sql("UPDATE safety_events SET severity='Invalid' WHERE event_id=?").param(event).update());
    }

    private JsonNode send(String path, String user, String text) throws Exception {
        return execute(post(path),user,Map.of("requestId",UUID.randomUUID(),"text",text),200);
    }

    private void insertReference(UUID id, String status, int days, String title) {
        jdbc.sql("""
            INSERT INTO kb_items(content_id,title,body,language,topic,source,reviewed_date,expires_on,status)
            VALUES (?,?,'Exercice de démonstration.','French','TRANSPORT','E3',
                (now() AT TIME ZONE 'UTC')::date,(now() AT TIME ZONE 'UTC')::date + ?,?)
            """).params(id,title,days,status).update();
    }

    @Test
    void directoryCreationFilteringVerificationAndPermissions() throws Exception {
        String content = login("demo.content_team",true);
        String user = login("demo.user",false);
        String admin = "/api/admin/organisations";
        String label = "Test-" + UUID.randomUUID();
        var request = new HashMap<String,Object>();
        request.put("name",label);
        request.put("governorate","TN-34");
        request.put("serviceType","TRANSPORT");
        request.put("cost","FREE");
        request.put("languages",List.of("French","French"));
        execute(post(admin),user,request,403);
        execute(post(admin),content,request,400);
        request.put("languages",List.of("French"));
        var created = execute(post(admin),content,request,201);
        String id = created.get("id").asText();
        assertTrue(created.get("verificationDue").asBoolean());
        request.put("name",label+" updated");
        execute(patch(admin+"/"+id),content,request,200);
        var results = execute(get("/api/organisations").param("q",label).param("governorate","TN-34")
            .param("category","TRANSPORT"),null,null,200);
        assertEquals(id,results.at("/items/0/id").asText());
        var verified = execute(post(admin+"/"+id+"/verify"),content,Map.of("method","Exercice"),200);
        assertFalse(verified.get("verificationDue").asBoolean());
    }

    private String login(String username, boolean staff) throws Exception {
        return execute(post("/api/auth/login"),null,Map.of("username",username,"password",password,"staff",staff),200)
                .get("token").asText();
    }

    private JsonNode execute(MockHttpServletRequestBuilder request, String token, Object body, int status) throws Exception {
        if (token != null) request.header("Authorization","Bearer "+token);
        if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
        var response = mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse();
        return response.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(response.getContentAsByteArray());
    }
}
