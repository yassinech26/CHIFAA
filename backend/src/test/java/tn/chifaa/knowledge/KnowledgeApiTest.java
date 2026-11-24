package tn.chifaa.knowledge;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "CHIFAA_JWT_ISSUER_URI=https://issuer.test", "CHIFAA_JWT_AUDIENCE=chifaa-test",
    "spring.flyway.locations=classpath:fixture,classpath:db/migration",
    "spring.flyway.placeholders.at044Source1=Test A", "spring.flyway.placeholders.at044Source2=Test B",
    "spring.flyway.placeholders.at044Source3=Test C", "spring.flyway.placeholders.at044Source4=Test D",
    "spring.flyway.placeholders.at044Source5=Test E"
})
@AutoConfigureMockMvc
@Sql(scripts = "/knowledge-api-fixtures.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class KnowledgeApiTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Database {
        @Bean(destroyMethod = "close")
        EmbeddedPostgres postgres() throws IOException {
            return EmbeddedPostgres.start();
        }

        @Bean
        DataSource dataSource(EmbeddedPostgres postgres) {
            return postgres.getPostgresDatabase();
        }
    }

    private static RSAKey signingKey;
    private static HttpServer keys;
    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void signingKeys(DynamicPropertyRegistry properties) throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test").generate();
        byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        keys = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keys.createContext("/keys", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (var out = exchange.getResponseBody()) {
                out.write(jwks);
            }
        });
        keys.start();
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + keys.getAddress().getPort() + "/keys");
    }

    @AfterAll
    static void closeKeys() {
        if (keys != null) keys.stop(0);
    }

    private String bearer(String audience) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer("https://issuer.test").subject("test-user")
                .audience(audience).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims);
        token.sign(new RSASSASigner(signingKey));
        return "Bearer " + token.serialize();
    }

    private MockHttpServletRequestBuilder read(String path) throws Exception {
        return get(path).header("Authorization", bearer("chifaa-test"));
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
        mvc.perform(get(path).header("Authorization", bearer("other-app"))).andExpect(status().isUnauthorized());
    }
}
