package com.answerbase.api;

import com.answerbase.core.models.JsonHttp;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class SupabaseVerifiedEmailProvider implements VerifiedEmailProvider {
    private final String endpoint, key;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    SupabaseVerifiedEmailProvider(
            @Value("${answerbase.auth.issuer:}") String issuer,
            @Value("${answerbase.auth.jwk-set-uri:}") String jwks,
            @Value("${SUPABASE_PUBLISHABLE_KEY:}") String key) {
        this.endpoint =
                (jwks.isBlank() ? issuer : jwks.replaceFirst("/\\.well-known/jwks\\.json$", ""))
                        + "/user";
        this.key = key;
    }

    public String email(Jwt jwt) {
        try {
            var response =
                    http.send(
                            HttpRequest.newBuilder(URI.create(endpoint))
                                    .timeout(Duration.ofSeconds(10))
                                    .header("Authorization", "Bearer " + jwt.getTokenValue())
                                    .header("apikey", key)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IllegalStateException("Identity verification unavailable");
            var user = JsonHttp.JSON.readTree(response.body());
            String email = user.path("email").asText("");
            if (!jwt.getSubject().equals(user.path("id").asText())
                    || email.isBlank()
                    || user.path("email_confirmed_at").asText("").isBlank())
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Confirm your email before accepting an invitation");
            return email;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Identity verification interrupted");
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to verify your email with the identity provider. Retry later.");
        }
    }
}
