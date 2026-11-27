package tn.chifaa.clinicalreview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tn.chifaa.PostgresJwtTest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {"chifaa.chat.kb-version=KB-20261125","chifaa.chat.triggers[0].phrase=signal fictif",
        "chifaa.chat.triggers[0].type=Test 1","chifaa.chat.triggers[0].severity=High"})
class ClinicalReviewApiTest extends PostgresJwtTest {
    private static final String ROOT = "/api/admin/kb";
    private static final String CREATE = """
            {"title":"Atlas fictif","topic":"Accueil","language":"French","contentText":"Version de démonstration",
             "sourceType":"Test A","sourceReference":"Source de test","evidenceLevel":"Low","localApplicability":"Applies"}
            """;
    private static final String APPROVE = "{\"approvalStatus\":\"Clinically approved\"}";
    private static final String RETIRE = "{\"approvalStatus\":\"Retired\"}";
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired org.flywaydb.core.Flyway flyway;
    private String editor,reviewer,mobile;

    @BeforeEach void staff() throws Exception {
        db.update("delete from kb_items");
        db.update("""
                insert into staff(staff_id,role,active) values ('CT-B2','Content team',true),
                  ('CR-B2','Clinical reviewer',true),('CR-OFF','Clinical reviewer',false),
                  ('AD-B2','Admin',true),('SR-B2','Safety reviewer',true),('DS-B2','Data steward',true)
                on conflict (staff_id) do update set role=excluded.role,active=excluded.active
                """);
        editor = token("CT-B2"); reviewer = token("CR-B2");
        mobile = patientBearer(patient(false));
    }

    private String token(String staff) throws Exception { return bearer(staff,"chifaa-test"); }
    private ResultActions request(MockHttpServletRequestBuilder r,String auth) throws Exception {
        return mvc.perform(r.header("Authorization",auth).contentType("application/json"));
    }
    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsByteArray());
    }
    private String draft() throws Exception {
        var created = request(post(ROOT).content(CREATE),editor).andExpect(status().isCreated());
        var item = read(created);
        String id = item.get("contentId").asText();
        created.andExpect(header().string("Location",ROOT+"/"+id));
        assertTrue(item.get("fictional").asBoolean()); assertTrue(item.get("clinicalReviewer").isNull());
        assertEquals("Contenu fictif – non validé",item.get("notice").asText());
        assertEquals("Draft",item.get("approvalStatus").asText()); assertEquals("v1.0",item.get("contentVersion").asText());
        return id;
    }
    private ResultActions change(String id,String body,String auth) throws Exception {
        return request(patch(ROOT+"/"+id).content(body),auth);
    }
    private JsonNode success(String id,String body,String auth) throws Exception {
        return read(change(id,body,auth).andExpect(status().isOk()));
    }
    private ResultActions published(String id) throws Exception { return request(get("/api/kb/"+id),mobile); }

    @Test void editorAndReviewerWorkOnExactVersionsAndReadersSeeOnlyThePublication() throws Exception {
        String initial = draft();
        published(initial).andExpect(status().isNotFound());
        request(get(ROOT).param("status","Draft"),reviewer).andExpect(jsonPath("$[0].contentId").value(initial));
        String first = success(initial,"{\"title\":\"Atlas relu\"}",editor).get("contentId").asText();
        assertNotEquals(initial,first);
        change(initial,APPROVE,reviewer).andExpect(status().isConflict());
        var checked = success(first,APPROVE,reviewer);
        assertEquals("CR-B2",checked.get("clinicalReviewer").asText());
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(),checked.get("reviewDate").asText());
        assertEquals(checked,success(first,APPROVE,reviewer));
        published(first).andExpect(jsonPath("$.notice").value("Contenu fictif – non validé"));

        var candidate = success(first,"{\"contentText\":\"Seconde édition fictive\"}",editor);
        String second = candidate.get("contentId").asText();
        assertTrue(candidate.get("reviewDate").isNull());
        assertTrue(candidate.get("clinicalReviewer").isNull());
        change(first,"{\"title\":\"Concurrence\"}",editor).andExpect(status().isConflict());
        String last = success(second,"{\"evidenceLevel\":null}",editor).get("contentId").asText();
        published(first).andExpect(jsonPath("$.contentText").value("Version de démonstration"));
        published(last).andExpect(status().isNotFound());
        var approved = success(last,APPROVE,reviewer);
        assertEquals("v1.3",approved.get("contentVersion").asText());
        assertTrue(approved.get("evidenceLevel").isNull());
        published(first).andExpect(status().isNotFound());
        published(last).andExpect(jsonPath("$.contentText").value("Seconde édition fictive"));
        request(get(ROOT+"/"+first),editor).andExpect(jsonPath("$.supersededBy").value(second));
        request(get(ROOT+"/"+second),reviewer).andExpect(jsonPath("$.supersededBy").value(last));
        request(get(ROOT+"/"+last+"/versions"),editor).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].contentId").value(initial)).andExpect(jsonPath("$[3].contentId").value(last));
        request(get(ROOT+"/"+initial+"/versions").param("offset","1").param("limit","1"),reviewer)
                .andExpect(jsonPath("$[0].contentId").value(first));
        success(last,RETIRE,reviewer);
        published(last).andExpect(status().isNotFound());
        change(last,APPROVE,reviewer).andExpect(status().isConflict());
    }

    @Test void rejectionOfCandidatePreservesPublicationAndWithdrawalCancelsPendingPublication() throws Exception {
        String original = draft();
        success(original,APPROVE,reviewer);
        String rejected = success(original,"{\"title\":\"Essai un\"}",editor).get("contentId").asText();
        success(rejected,RETIRE,reviewer);
        published(original).andExpect(status().isOk());
        var next = success(original,"{\"title\":\"Essai deux\"}",editor);
        assertEquals("v1.2",next.get("contentVersion").asText());
        String pending = next.get("contentId").asText();
        success(original,RETIRE,reviewer);
        change(pending,APPROVE,reviewer).andExpect(status().isConflict());
        published(original).andExpect(status().isNotFound());
        published(pending).andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(strings={"AD-B2","DS-B2","SR-B2","CR-OFF","ABSENT","c96eca0c-b68b-4981-9652-b9a96efed9a5"})
    void otherAccountsCannotReadDraftsOrMakeDecisions(String subject) throws Exception {
        String auth=token(subject), id=draft();
        request(get(ROOT),auth).andExpect(status().isForbidden());
        request(get(ROOT+"/"+id),auth).andExpect(status().isForbidden());
        request(get(ROOT+"/"+id+"/versions"),auth).andExpect(status().isForbidden());
        request(post(ROOT).content(CREATE),auth).andExpect(status().isForbidden());
        change(id,APPROVE,auth).andExpect(status().isForbidden());
        change(id,RETIRE,auth).andExpect(status().isForbidden());
    }

    @Test void rolesAreSeparatedAndLiveRevocationAppliesToValidTokens() throws Exception {
        String id = draft();
        mvc.perform(post(ROOT).contentType("application/json").content(CREATE)).andExpect(status().isUnauthorized());
        mvc.perform(patch(ROOT+"/"+id).contentType("application/json").content(APPROVE)).andExpect(status().isUnauthorized());
        change(id,APPROVE,editor).andExpect(status().isForbidden());
        change(id,RETIRE,editor).andExpect(status().isForbidden());
        change(id,"{\"title\":\"Nouveau\"}",reviewer).andExpect(status().isForbidden());
        request(post(ROOT).content(CREATE),reviewer).andExpect(status().isForbidden());
        db.update("update staff set active=false where staff_id='CR-B2'");
        change(id,APPROVE,reviewer).andExpect(status().isForbidden());
        published(id).andExpect(status().isNotFound());
    }

    @Test void invalidMetadataAndExpiredApprovalRollbackAtomically() throws Exception {
        String id = draft();
        for (String invalid : List.of("{\"title\":null}","{\"topic\":\" \"}","{\"sourceType\":\"Autre\"}",
                "{\"language\":\"Autre\"}","{\"expiryDate\":\"2026-02-30\"}","{\"clinicalReviewer\":\"CR-B2\"}",
                "{\"fictional\":false}","{\"contentVersion\":\"v8.0\"}","{\"supersededBy\":\"KB-0042\"}","{}"))
            change(id,invalid,editor).andExpect(status().isBadRequest());
        change(id,"{\"approvalStatus\":\"Clinically approved\",\"contentText\":\"Autre texte\"}",reviewer)
                .andExpect(status().isBadRequest());
        assertEquals(id,success(id,"{\"title\":\"Atlas fictif\"}",editor).get("contentId").asText());
        success(id,APPROVE,reviewer);
        String next = success(id,"{\"title\":\"Autre version\"}",editor).get("contentId").asText();
        change(next,"{\"approvalStatus\":\"Clinically approved\",\"expiryDate\":\"2000-01-01\"}",reviewer)
                .andExpect(status().isBadRequest());
        published(id).andExpect(status().isOk());
        published(next).andExpect(status().isNotFound());
        var approved = success(next,"{\"approvalStatus\":\"Clinically approved\",\"expiryDate\":\"2099-12-31\"}",reviewer);
        assertEquals("2099-12-31",approved.get("expiryDate").asText());
    }

    @Test void concurrentEditsCannotBranchOrOverwriteTheSameDraft() throws Exception {
        String id = draft();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Integer> edit = () -> {
            start.await();
            return change(id,"{\"title\":\"Révision simultanée\"}",editor).andReturn().getResponse().getStatus();
        };
        try {
            var first = pool.submit(edit); var second = pool.submit(edit);
            start.countDown();
            assertEquals(List.of(200,409),java.util.stream.Stream.of(first.get(10,TimeUnit.SECONDS),
                    second.get(10,TimeUnit.SECONDS)).sorted().toList());
        } finally { pool.shutdownNow(); }
        request(get(ROOT+"/"+id+"/versions"),editor).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void chatUsesOnlyTheReviewedVersionAndMasksWithdrawnReferences() throws Exception {
        String auth = patientBearer(patient(true));
        var session = read(request(post("/api/chat/sessions"),auth).andExpect(status().isCreated()));
        String messages = "/api/chat/sessions/"+session.get("sessionId").asText()+"/messages";
        String ask = "{\"text\":\"Atlas\"}";
        String first = draft();
        request(post(messages).content(ask),auth).andExpect(jsonPath("$.answer.replyKind").value("Directory"));
        success(first,APPROVE,reviewer);
        String next = success(first,"{\"contentText\":\"Nouveau texte fictif\"}",editor).get("contentId").asText();
        request(post(messages).content(ask),auth).andExpect(jsonPath("$.answer.contentId").value(first));
        success(next,APPROVE,reviewer);
        request(post(messages).content(ask),auth).andExpect(jsonPath("$.answer.contentId").value(next));
        success(next,RETIRE,reviewer);
        request(post(messages).content(ask),auth).andExpect(jsonPath("$.answer.replyKind").value("Directory"));
        request(get(messages),auth).andExpect(jsonPath("$[3].replyKind").value("ReferenceUnavailable"))
                .andExpect(jsonPath("$[5].replyKind").value("ReferenceUnavailable"));
    }

    @Test void migrationRestoresHistoryForAnExistingPublishedChain() {
        var legacy = org.flywaydb.core.Flyway.configure().configuration(flyway.getConfiguration())
                .schemas("legacy_b2").defaultSchema("legacy_b2");
        legacy.target("2026112501").load().migrate();
        db.update("""
                insert into legacy_b2.kb_items(content_id,title,topic,language,content_text,source_type,source_reference,
                    content_version,approval_status,clinical_reviewer,review_date)
                values ('KB-0042','Exemple','Accueil','French','Texte','Test A','Source','v2.0','Clinically approved',
                        'TEST-01',current_date), ('KB-0314','Ancienne','Accueil','French','Ancien','Test A','Source',
                        'v1.0','Retired',null,null)
                """);
        db.update("update legacy_b2.kb_items set superseded_by='KB-0042' where content_id='KB-0314'");
        legacy.target("2026112502").load().migrate();
        assertEquals("KB-0314",db.queryForObject("""
                select previous_version_id from legacy_b2.kb_items where content_id='KB-0042'
                """,String.class));
        assertEquals("KB-0042",db.queryForObject("select content_id from legacy_b2.kb_visible_items",String.class));
    }
}
