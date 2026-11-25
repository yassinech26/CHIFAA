package tn.chifaa;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;


@SpringBootTest(properties = {
    "CHIFAA_JWT_ISSUER_URI=https://issuer.test", "CHIFAA_JWT_AUDIENCE=chifaa-test",
    "spring.flyway.locations=classpath:fixture,classpath:db/migration",
    "spring.flyway.placeholders.at044Source1=Test A", "spring.flyway.placeholders.at044Source2=Test B",
    "spring.flyway.placeholders.at044Source3=Test C", "spring.flyway.placeholders.at044Source4=Test D",
    "spring.flyway.placeholders.at044Source5=Test E",
    "spring.flyway.placeholders.at022Type1=Test 1",
    "spring.flyway.placeholders.at022Type2=Test 2",
    "spring.flyway.placeholders.at022Type3=Test 3",
    "spring.flyway.placeholders.at022Type4=Test 4",
    "spring.flyway.placeholders.at022Type5=Test 5",
    "spring.flyway.placeholders.at022Type6=Test 6",
    "spring.flyway.placeholders.at022Type7=Test 7"
})
@org.springframework.context.annotation.Import(PostgresJwtTest.Database.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresJwtTest {
    @TestConfiguration(proxyBeanMethods = false)
    public static class Database {
        @Bean(destroyMethod = "close")
        EmbeddedPostgres postgres() throws IOException {
            return EmbeddedPostgres.builder().setServerConfig("log_error_verbosity","terse")
                    .setServerConfig("log_min_error_statement","panic").start();
        }

        @Bean
        DataSource dataSource(EmbeddedPostgres postgres) {
            return postgres.getPostgresDatabase();
        }
    }

    private static RSAKey signingKey;
    private static HttpServer keys;
    @Autowired protected MockMvc mvc;

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

    protected String bearer(String subject, String audience) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer("https://issuer.test").subject(subject)
                .audience(audience).expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims);
        token.sign(new RSASSASigner(signingKey));
        return "Bearer " + token.serialize();
    }

}
