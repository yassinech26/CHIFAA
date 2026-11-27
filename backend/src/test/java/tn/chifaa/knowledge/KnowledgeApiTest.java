package tn.chifaa.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tn.chifaa.PostgresJwtTest;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Sql(scripts = "/knowledge-api-fixtures.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class KnowledgeApiTest extends PostgresJwtTest {
    private String access;

    @org.junit.jupiter.api.BeforeEach void signIn() { access = patientBearer(patient(false)); }

    private MockHttpServletRequestBuilder read(String path) throws Exception {
        return get(path).header("Authorization", access);
    }

    @Test
    void paginatesOnlyVisibleSummaries() throws Exception {
        mvc.perform(read("/api/kb").param("limit", "2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].contentId", contains("KB-0001", "KB-0002")))
                .andExpect(jsonPath("$.items[0].notice").value("Contenu fictif – non validé"))
                .andExpect(jsonPath("$.items[0].contentText").doesNotExist())
                .andExpect(jsonPath("$.nextAfter").value("KB-0002"));
        mvc.perform(read("/api/kb").param("limit", "2").param("after", "KB-0002"))
                .andExpect(jsonPath("$.items[*].contentId", contains("KB-0003")))
                .andExpect(jsonPath("$.nextAfter").value(nullValue()));
    }

    @Test
    void combinesLiteralKeywordTopicAndLanguage() throws Exception {
        mvc.perform(read("/api/kb").param("q", " ALPHA ")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].contentId", contains("KB-0001", "KB-0003")));
        mvc.perform(read("/api/kb").param("q", "ALPHA").param("topic", " accueil ").param("language", "French"))
                .andExpect(jsonPath("$.items[*].contentId", contains("KB-0001")));
        mvc.perform(read("/api/kb").param("q", "%_")).andExpect(jsonPath("$.items[*].contentId", contains("KB-0003")));
        mvc.perform(read("/api/kb").param("q", "تجريبي").param("language", "MSA"))
                .andExpect(jsonPath("$.items[*].contentId", contains("KB-0002")));
        mvc.perform(read("/api/kb").param("q", "alpha").param("language", "MSA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void returnsContentAndReviewMetadata() throws Exception {
        mvc.perform(read("/api/kb/KB-0001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.contentId").value("KB-0001"))
                .andExpect(jsonPath("$.contentText").value("Texte exemple"))
                .andExpect(jsonPath("$.sourceReference").value("Référence de test"))
                .andExpect(jsonPath("$.reviewDate").isString()).andExpect(jsonPath("$.expiryDate").isString())
                .andExpect(jsonPath("$.evidenceLevel").value("Low")).andExpect(jsonPath("$.fictional").value(true))
                .andExpect(jsonPath("$.notice").value("Contenu fictif – non validé"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"KB-0004", "KB-0005", "KB-0006", "KB-0007", "KB-0008", "KB-9999"})
    void hidesUnpublishedAndUnknownIds(String id) throws Exception {
        mvc.perform(read("/api/kb/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/kb?language=Mixed", "/api/kb?limit=0", "/api/kb?limit=101",
            "/api/kb?after=abc", "/api/kb/invalid"})
    void rejectsInvalidInput(String path) throws Exception {
        mvc.perform(read(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/kb", "/api/kb/KB-0001"})
    void requiresAValidAccessToken(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", bearer("test-user", "other-app"))).andExpect(status().isUnauthorized());
    }
}
