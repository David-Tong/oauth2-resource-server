package io.ytong.resourceserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    NimbusJwtDecoder jwtDecoder(@Value("${resource.issuer}") String issuer,
            @Value("${resource.jwk-set-uri}") String jwks,
            @Value("${resource.audience}") String audience) {
        if (issuer.isBlank() || jwks.isBlank() || audience.isBlank()) {
            throw new IllegalArgumentException("Issuer, JWKS URI and audience must be configured");
        }
        // Our authorization server issues RFC 9068 access tokens with typ=at+jwt.
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).validateType(false).build();
        OAuth2TokenValidator<Jwt> accessToken = jwt -> {
            boolean valid = "at+jwt".equals(jwt.getHeaders().get("typ"))
                    && jwt.getAudience().contains(audience)
                    && jwt.getExpiresAt() != null
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank();
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(issuer), accessToken));
        return decoder;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/resources/demo").hasAuthority("SCOPE_demo.read")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }
}
