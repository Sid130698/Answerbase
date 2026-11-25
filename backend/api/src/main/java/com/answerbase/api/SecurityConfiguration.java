package com.answerbase.api;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/** Stateless bearer authentication. Workspace authorization is a separate application concern. */
@Configuration
class SecurityConfiguration {
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${answerbase.auth.issuer:}") String issuer,
            @Value("${answerbase.auth.jwk-set-uri:}") String jwks) {
        if (issuer.isBlank()) {
            // Fail closed when a project has not been configured; public health/docs still work.
            return token -> {
                throw new BadJwtException("Authentication is not configured");
            };
        }
        var decoder =
                NimbusJwtDecoder.withJwkSetUri(
                                jwks.isBlank() ? issuer + "/.well-known/jwks.json" : jwks)
                        .jwsAlgorithms(
                                algorithms -> {
                                    algorithms.add(SignatureAlgorithm.ES256);
                                    algorithms.add(SignatureAlgorithm.RS256);
                                })
                        .build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new JwtClaimValidator<java.time.Instant>("exp", expiry -> expiry != null),
                        new JwtClaimValidator<Boolean>(
                                "is_anonymous", anonymous -> !Boolean.TRUE.equals(anonymous)),
                        new JwtClaimValidator<List<String>>(
                                "aud", aud -> aud != null && aud.contains("authenticated")),
                        new JwtClaimValidator<String>("role", "authenticated"::equals),
                        new JwtClaimValidator<String>(
                                "sub",
                                subject -> {
                                    try {
                                        return subject != null
                                                && UUID.fromString(subject)
                                                        .toString()
                                                        .equals(subject);
                                    } catch (IllegalArgumentException ignored) {
                                        return false;
                                    }
                                })));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Value("${springdoc.api-docs.enabled:true}") boolean apiDocsEnabled)
            throws Exception {
        return http.authorizeHttpRequests(
                        requests -> {
                            requests.requestMatchers(
                                            HttpMethod.GET,
                                            "/actuator/health",
                                            "/actuator/health/**")
                                    .permitAll();
                            if (apiDocsEnabled) {
                                requests.requestMatchers(
                                                HttpMethod.GET,
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/v3/api-docs.yaml",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**")
                                        .permitAll();
                            }
                            requests.requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated();
                            requests.requestMatchers(
                                            HttpMethod.GET,
                                            "/api/v1/workspaces",
                                            "/api/v1/workspaces/*")
                                    .authenticated();
                            requests.requestMatchers(HttpMethod.POST, "/api/v1/workspaces")
                                    .authenticated();
                            requests.requestMatchers(HttpMethod.GET, "/api/v1/workspaces/*/members")
                                    .authenticated();
                            requests.requestMatchers(
                                            HttpMethod.PUT, "/api/v1/workspaces/*/members/*")
                                    .authenticated();
                            requests.requestMatchers(
                                            HttpMethod.DELETE, "/api/v1/workspaces/*/members/*")
                                    .authenticated();
                            requests.requestMatchers(HttpMethod.POST, "/api/v1/organizations")
                                    .authenticated();
                            requests.requestMatchers(
                                            HttpMethod.GET,
                                            "/api/v1/workspaces/*/documents",
                                            "/api/v1/workspaces/*/documents/**")
                                    .authenticated();
                            requests.requestMatchers(
                                            HttpMethod.POST,
                                            "/api/v1/workspaces/*/documents",
                                            "/api/v1/workspaces/*/documents/**")
                                    .authenticated();
                            requests.requestMatchers(
                                            "/api/v1/workspaces/*/groups",
                                            "/api/v1/workspaces/*/groups/*/members",
                                            "/api/v1/workspaces/*/documents/*/access")
                                    .authenticated();
                            requests.requestMatchers(HttpMethod.POST, "/api/v1/workspaces/*/search")
                                    .authenticated();
                            requests.requestMatchers(
                                            "/api/v1/workspaces/*/questions",
                                            "/api/v1/workspaces/*/questions/**")
                                    .authenticated();
                            requests.requestMatchers("/api/v1/support/**").authenticated();
                            // Opaque, scoped support grants are verified inside the delegated
                            // service.
                            requests.requestMatchers("/api/v1/delegated/**").permitAll();
                            requests.anyRequest().denyAll();
                        })
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .build();
    }
}
