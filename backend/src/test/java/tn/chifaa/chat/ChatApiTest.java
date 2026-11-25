package tn.chifaa.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tn.chifaa.PostgresJwtTest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {
    "chifaa.chat.kb-version=KB-20261124",
    "chifaa.chat.triggers[0].phrase=signal test", "chifaa.chat.triggers[0].type=Test 1",
    "chifaa.chat.triggers[0].severity=High", "chifaa.chat.resources.fr=RESSOURCE TECHNIQUE DE TEST"
})
@Sql(scripts="/knowledge-api-fixtures.sql", executionPhase=Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ChatApiTest extends PostgresJwtTest {
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    private String user, other;
    private String session;
    private static final String ROOT = "/api/chat/sessions";

    @BeforeEach void accounts() throws Exception {
        user = account();
        other = account();
        session = json.readTree(mvc.perform(auth(post(ROOT),user)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray()).get("sessionId").asText();
    }

    private String account() {
        UUID id = UUID.randomUUID();
        db.update("insert into users values (?)",id);
        db.update("""
                insert into consents(consent_id,user_pid,consent_purpose,consent_status,given_at)
                values (concat('T',nextval('test_consent_seq')),?,'Health data','Given',now())
                """,id);
        return id.toString();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder req, String subject) throws Exception {
        return req.header("Authorization",bearer(subject,"chifaa-test")).contentType("application/json");
    }
    private String path() { return ROOT+"/"+session+"/messages"; }
    private MockHttpServletRequestBuilder ask(String who, String text, UUID id) throws Exception {
        return auth(post(path()),who).content(json.writeValueAsBytes(new ChatModels.Question(text,id)));
    }
    private JsonNode send(String text, UUID id) throws Exception {
        return json.readTree(mvc.perform(ask(user,text,id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    @Test void renewedTokenUsesLatestConsentForItsOwner() throws Exception {
        String renewed = bearer(user, "chifaa-test");
        String tokenB = bearer(other, "chifaa-test");
        mvc.perform(get(path()).header("Authorization", renewed)).andExpect(status().isOk());
        mvc.perform(get(path()).header("Authorization", tokenB)).andExpect(status().isNotFound());

        // E1 peut historiser le retrait tout en conservant la ligne Given précédente.
        db.update("""
                insert into consents(consent_id,user_pid,consent_purpose,consent_status,given_at,withdrawn_at)
                values (concat('T',nextval('test_consent_seq')),?,'Health data','Withdrawn',now(),now())
                """, UUID.fromString(user));
        mvc.perform(get(path()).header("Authorization", renewed)).andExpect(status().isForbidden());
        mvc.perform(get("/api/kb").header("Authorization", renewed)).andExpect(status().isOk());
        mvc.perform(post(ROOT).header("Authorization", tokenB)).andExpect(status().isCreated());
    }

    @Test void retrievesVisibleTextAndRecordsProvenanceAndRating() throws Exception {
        var turn = send("Bonjour, je cherche Alpha",null);
        var answer = turn.get("answer");
        assertEquals("KB-0001",answer.get("contentId").asText());
        assertEquals("Contenu fictif – non validé\nTexte exemple",answer.get("text").asText());
        assertEquals("Accueil",turn.get("question").get("topicTag").asText());
        assertEquals("KB-20261124",answer.get("kbVersion").asText());
        assertTrue(answer.get("responseLatencyMs").asInt() >= 0);
        String feedback = "/api/chat/messages/"+answer.get("messageId").asText()+"/feedback";
        mvc.perform(auth(post(feedback),user).content("{\"rating\":5}")).andExpect(status().isNoContent());
        mvc.perform(auth(get(path()),user)).andExpect(jsonPath("$[0].senderRole").value("User"))
                .andExpect(jsonPath("$[1].userFeedback").value(5));
        for (String value : List.of("0","6","1.5","null"))
            mvc.perform(auth(post(feedback),user).content("{\"rating\":"+value+"}")).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/chat/messages/"+turn.get("question").get("messageId").asText()+"/feedback"),user)
                .content("{\"rating\":2}")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource(delimiter='|',value={
        "Aucunmotconnu|French|Directory", "caché|French|Directory", "دليل تجريبي|MSA|Knowledge",
        "نحب alpha|Mixed|Knowledge", "nheb alpha|Darija|Knowledge",
        "Bonjour je veux un diagnostic alpha|French|MedicalLimit", "Quelle dose prendre pour Alpha ?|French|MedicalLimit",
        "ما هي جرعة الدواء؟|MSA|MedicalLimit", "9adech nekhou alpha|Darija|MedicalLimit",
        "Est-ce que j’ai un cancer ?|French|MedicalLimit"
    })
    void routesWithoutInventingContent(String text,String lang,String kind) throws Exception {
        var result = send(text,null).get("answer");
        assertEquals(lang,result.get("detectedLanguage").asText());
        assertEquals(kind,result.get("replyKind").asText());
        if (lang.equals("Mixed")) assertEquals("KB-0003",result.get("contentId").asText());
        if (!kind.equals("Knowledge")) assertTrue(result.get("contentId").isNull());
    }

    @ParameterizedTest
    @CsvSource(delimiter='|',value={
        "Quelle dose prendre ?|MedicalLimit|Je ne peux pas poser de diagnostic",
        "ما هي جرعة الدواء؟|MedicalLimit|لتشخيص الحالة",
        "Aucunmotconnu|Directory|Je n’ai trouvé aucune fiche",
        "مجهول|Directory|لم أجد بطاقة مناسبة",
        "signal test|Distress|Vous pouvez contacter",
        "signal test مساعدة|Distress|يمكنك التواصل",
        "دليل تجريبي|Knowledge|محتوى افتراضي – غير معتمد"
    })
    void sendsLocalizedReplyAndPreservesItInHistory(String question,String kind,String prefix) throws Exception {
        var answer = send(question,null).get("answer");
        String text = answer.get("text").asText();
        assertEquals(kind,answer.get("replyKind").asText());
        assertTrue(text.startsWith(prefix),text);
        if (kind.equals("Distress")) assertTrue(text.endsWith("\nRESSOURCE TECHNIQUE DE TEST"));
        mvc.perform(auth(get(path()),user)).andExpect(jsonPath("$[1].text").value(text));
    }

    @Test void distressWinsAndRetryDoesNotDuplicateEventOrTurn() throws Exception {
        UUID id = UUID.randomUUID();
        String text = "SIGNAL-test ! Quelle dose pour Alpha ?";
        var answer = send(text,id);
        assertEquals(answer,send(text,id));
        assertTrue(answer.get("question").get("distressFlag").asBoolean());
        assertTrue(answer.get("answer").get("text").asText().contains("RESSOURCE TECHNIQUE DE TEST"));
        assertEquals("Distress",answer.get("answer").get("replyKind").asText());
        assertEquals(1,db.queryForObject("""
                select count(*) from safety_events where session_id=? and user_pid=? and event_status='Open'
                and trigger_type='Test 1' and severity='High' and flagged_at is not null
                """,Integer.class,UUID.fromString(session),UUID.fromString(user)));
        mvc.perform(ask(user,"texte différent",id)).andExpect(status().isConflict());
        mvc.perform(auth(get(path()),user)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void restrictsAllRoutesToOwnerAndChecksCurrentConsent() throws Exception {
        var answer = send("Alpha",null).get("answer");
        String feedback = "/api/chat/messages/"+answer.get("messageId").asText()+"/feedback";
        mvc.perform(auth(get(ROOT),other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(get(path()),other)).andExpect(status().isNotFound());
        mvc.perform(ask(other,"signal test",null)).andExpect(status().isNotFound());
        mvc.perform(auth(post(feedback),other).content("{\"rating\":2}")).andExpect(status().isNotFound());
        for (var route : List.of(post(ROOT),get(ROOT),get(path()),post(path()),post(feedback)))
            mvc.perform(route).andExpect(status().isUnauthorized());
        db.update("update consents set consent_status='Withdrawn', withdrawn_at=now() where user_pid=?",UUID.fromString(user));
        mvc.perform(auth(get(path()),user)).andExpect(status().isForbidden());
        mvc.perform(ask(user,"Alpha",null)).andExpect(status().isForbidden());
        mvc.perform(auth(post(feedback),user).content("{\"rating\":2}")).andExpect(status().isForbidden());
        mvc.perform(auth(post(ROOT),user)).andExpect(status().isForbidden());
    }

    @Test void historyAndRetriesMaskWithdrawnReference() throws Exception {
        UUID id = UUID.randomUUID();
        send("Alpha",id);
        try {
            db.update("update kb_items set approval_status='Retired' where content_id='KB-0001'");
            mvc.perform(auth(get(path()),user)).andExpect(jsonPath("$[1].replyKind").value("ReferenceUnavailable"))
                    .andExpect(jsonPath("$[1].text").doesNotExist());
            assertTrue(send("Alpha",id).get("answer").get("contentId").isNull());
        } finally { db.update("update kb_items set approval_status='Clinically approved' where content_id='KB-0001'"); }
    }

    @Test void failedAlertRollsBackTheWholeTurn() throws Exception {
        UUID id = UUID.randomUUID();
        // Simule une file indisponible entre l'enregistrement de la question et celui de l'alerte.
        db.execute("alter table safety_events add constraint simulate_unavailable_queue check (false) not valid");
        try {
            mvc.perform(ask(user,"signal test",id)).andExpect(status().isServiceUnavailable());
            assertEquals(0,db.queryForObject("select count(*) from messages where session_id=?",Integer.class,UUID.fromString(session)));
        } finally { db.execute("alter table safety_events drop constraint simulate_unavailable_queue"); }
        assertEquals("Distress", send("signal test",id).get("answer").get("replyKind").asText());
    }

    @Test void simultaneousRetriesShareOneCommittedTurn() throws Exception {
        UUID id = UUID.randomUUID();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<JsonNode> submit = () -> {
            start.await(10, java.util.concurrent.TimeUnit.SECONDS);
            return send("signal test",id);
        };
        try {
            var first = pool.submit(submit);
            var second = pool.submit(submit);
            assertEquals(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,db.queryForObject("select count(*) from safety_events where session_id=?",
                    Integer.class,UUID.fromString(session)));
            assertEquals(2,db.queryForObject("select count(*) from messages where session_id=?",
                    Integer.class,UUID.fromString(session)));
        } finally { pool.shutdownNow(); }
    }

    @Test void rejectsInvalidQuestionsAndLimitsHistory() throws Exception {
        for (String invalid : List.of("   ", "a".repeat(4001),"a\u0000b"))
            mvc.perform(ask(user,invalid,null)).andExpect(status().isBadRequest());
        send("Alpha",null);
        mvc.perform(auth(get(path()),user).param("offset","1").param("limit","1"))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].senderRole").value("AI"));
        mvc.perform(auth(get(ROOT),user).param("limit","101")).andExpect(status().isBadRequest());
    }
}
