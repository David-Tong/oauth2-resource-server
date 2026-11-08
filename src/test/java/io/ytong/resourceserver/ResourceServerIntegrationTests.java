package io.ytong.resourceserver;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceServerIntegrationTests {
    static final RSAKey KEY;
    static final HttpServer JWKS;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            JWKS = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] bytes = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            JWKS.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("resource.jwk-set-uri", () -> "http://localhost:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stop() { JWKS.stop(0); }

    @Autowired MockMvc mvc;

    String token(String issuer, String audience, String scope, Instant expires, String type, RSAKey key) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("user-001").audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(600))).claim("scope", scope);
        if (expires != null) claims.expirationTime(Date.from(expires));
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType(type)).keyID(KEY.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    String token(String issuer, String audience, String scope, Instant expires) throws Exception {
        return token(issuer, audience, scope, expires, "at+jwt", KEY);
    }

    @Test
    void validSignedTokenReadsResourceWithoutSession() throws Exception {
        mvc.perform(get("/api/resources/demo").header("Authorization", "Bearer " + token(
                "http://localhost:9001", "demo-api", "demo.read", Instant.now().plusSeconds(300))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subject").value("user-001"))
                .andExpect(jsonPath("$.items").isArray()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void missingAndMalformedTokensAreUnauthorized() throws Exception {
        mvc.perform(get("/api/resources/demo")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
        mvc.perform(get("/api/resources/demo").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongIssuerAudienceExpiryAndMissingExpiry() throws Exception {
        for (String jwt : new String[] {
            token("https://wrong.example", "demo-api", "demo.read", Instant.now().plusSeconds(300)),
            token("http://localhost:9001", "other-api", "demo.read", Instant.now().plusSeconds(300)),
            token("http://localhost:9001", "demo-api", "demo.read", Instant.now().minusSeconds(120)),
            token("http://localhost:9001", "demo-api", "demo.read", null),
            token("http://localhost:9001", "demo-api", "demo.read", Instant.now().plusSeconds(300), "JWT", KEY),
            token("http://localhost:9001", "demo-api", "demo.read", Instant.now().plusSeconds(300), "at+jwt", new RSAKeyGenerator(2048).generate())
        }) {
            mvc.perform(get("/api/resources/demo").header("Authorization", "Bearer " + jwt))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void missingScopeAndUnrelatedRoutesAreForbidden() throws Exception {
        String jwt = token("http://localhost:9001", "demo-api", "other.read", Instant.now().plusSeconds(300));
        mvc.perform(get("/api/resources/demo").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
        jwt = token("http://localhost:9001", "demo-api", "demo.read", Instant.now().plusSeconds(300));
        mvc.perform(get("/unrelated").header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
        mvc.perform(post("/api/resources/demo").header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
    }
}
