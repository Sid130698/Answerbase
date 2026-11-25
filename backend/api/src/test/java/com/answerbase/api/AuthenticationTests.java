package com.answerbase.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationTests {
    private static final String ISSUER = "https://auth.example.test/auth/v1";
    private static final String USER = UUID.randomUUID().toString();
    private static final RSAKey KEY;
    private static final HttpServer JWKS;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext(
                    "/jwks",
                    exchange -> {
                        byte[] body =
                                new JWKSet(KEY.toPublicJWK())
                                        .toString()
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        try (var out = exchange.getResponseBody()) {
                            out.write(body);
                        }
                    });
            JWKS.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @LocalServerPort int port;

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("answerbase.auth.issuer", () -> ISSUER);
        registry.add(
                "answerbase.auth.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwks() {
        JWKS.stop(0);
    }

    @Test
    void verifiedIdentityCanReadOnlyItsIdentity() throws Exception {
        var response =
                get(
                        "/api/v1/me",
                        token(
                                ISSUER,
                                "authenticated",
                                "authenticated",
                                USER,
                                Instant.now().plusSeconds(300),
                                KEY));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(USER, "person@example.test");
        assertThat(
                        get(
                                        "/api/v1/workspaces",
                                        token(
                                                ISSUER,
                                                "authenticated",
                                                "authenticated",
                                                USER,
                                                Instant.now().plusSeconds(300),
                                                KEY))
                                .statusCode())
                .isEqualTo(403);
    }

    @Test
    void invalidTokensAreRejectedByTheRealDecoder() throws Exception {
        assertThat(get("/api/v1/me", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/me", "not-a-jwt").statusCode()).isEqualTo(401);
        for (String jwt :
                java.util.List.of(
                        token(
                                "https://wrong.test",
                                "authenticated",
                                "authenticated",
                                USER,
                                Instant.now().plusSeconds(300),
                                KEY),
                        token(
                                ISSUER,
                                "other-app",
                                "authenticated",
                                USER,
                                Instant.now().plusSeconds(300),
                                KEY),
                        token(
                                ISSUER,
                                "authenticated",
                                "anon",
                                USER,
                                Instant.now().plusSeconds(300),
                                KEY),
                        token(
                                ISSUER,
                                "authenticated",
                                "authenticated",
                                "not-a-user",
                                Instant.now().plusSeconds(300),
                                KEY),
                        token(
                                ISSUER,
                                "authenticated",
                                "authenticated",
                                USER,
                                Instant.now().minusSeconds(120),
                                KEY),
                        token(
                                ISSUER,
                                "authenticated",
                                "authenticated",
                                USER,
                                Instant.now().plusSeconds(300),
                                new RSAKeyGenerator(2048).keyID("test-key").generate()))) {
            assertThat(get("/api/v1/me", jwt).statusCode()).isEqualTo(401);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String userToken(String user) throws Exception {
        return token(
                ISSUER,
                "authenticated",
                "authenticated",
                user,
                Instant.now().plusSeconds(300),
                KEY);
    }

    private HttpResponse<String> create(String jwt, String key, String body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request =
                    HttpRequest.newBuilder(
                                    URI.create("http://localhost:" + port + "/api/v1/workspaces"))
                            .header("Content-Type", "application/json")
                            .header("Idempotency-Key", key)
                            .POST(HttpRequest.BodyPublishers.ofString(body));
            if (jwt != null) request.header("Authorization", "Bearer " + jwt);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void workspaceCreationIsAtomicRetryableAndPrivate() throws Exception {
        String owner = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();
        String jwt = userToken(owner);
        UUID org = provision(owner, "owner", null);
        UUID otherOrg = provision(other, "owner", null);
        try {
            assertThat(create(null, key, "{\"name\":\"Support\"}").statusCode()).isEqualTo(401);
            assertThat(create(jwt, key, "{\"name\":\"   \"}").statusCode()).isEqualTo(400);
            assertThat(create(jwt, key, "{}").statusCode()).isEqualTo(400);
            assertThat(create(jwt, "invalid", "{\"name\":\"Support\"}").statusCode())
                    .isEqualTo(400);
            var created = create(jwt, key, "{\"name\":\" Support \"}");
            assertThat(created.statusCode()).isEqualTo(201);
            var json =
                    tools.jackson.databind.json.JsonMapper.builder()
                            .build()
                            .readTree(created.body());
            String id = json.path("id").asText();
            assertThat(json.path("role").asText()).isEqualTo("admin");
            assertThat(json.path("name").asText()).isEqualTo("Support");
            assertThat(get("/api/v1/workspaces/" + id, jwt).statusCode()).isEqualTo(200);
            assertThat(get("/api/v1/workspaces", jwt).body()).contains(id);
            assertThat(get("/api/v1/workspaces/" + id, userToken(other)).statusCode())
                    .isEqualTo(404);
            assertThat(get("/api/v1/workspaces", userToken(other)).body()).isEqualTo("[]");
            var replay = create(jwt, key, "{\"name\":\"Support\"}");
            assertThat(replay.statusCode()).isEqualTo(200);
            assertThat(replay.body()).isEqualTo(created.body());
            assertThat(create(jwt, key, "{\"name\":\"Changed\"}").statusCode()).isEqualTo(409);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM answerbase.workspace_members WHERE user_id = ?",
                                    Integer.class,
                                    UUID.fromString(owner)))
                    .isEqualTo(1);
            // Two simultaneous requests for the same new key create exactly one workspace.
            String concurrentKey = UUID.randomUUID().toString();
            try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var first =
                        pool.submit(() -> create(jwt, concurrentKey, "{\"name\":\"Operations\"}"));
                var second =
                        pool.submit(() -> create(jwt, concurrentKey, "{\"name\":\"Operations\"}"));
                var a = first.get();
                var b = second.get();
                assertThat(java.util.List.of(a.statusCode(), b.statusCode()))
                        .containsExactlyInAnyOrder(200, 201);
                assertThat(a.body()).isEqualTo(b.body());
            }
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM answerbase.workspaces WHERE created_by = ?",
                                    Integer.class,
                                    UUID.fromString(owner)))
                    .isEqualTo(2);
        } finally {
            jdbc.update(
                    "DELETE FROM answerbase.workspaces WHERE created_by = ?",
                    UUID.fromString(owner));
            cleanup(org);
            cleanup(otherOrg);
        }
    }

    private UUID provision(String user, String role, UUID organization) {
        UUID org = organization == null ? UUID.randomUUID() : organization;
        if (organization == null)
            jdbc.update(
                    "INSERT INTO answerbase.organizations (id,name) VALUES (?, 'Test company')",
                    org);
        jdbc.update(
                "INSERT INTO answerbase.users (id,organization_id,organization_role) VALUES (?, ?, ?)",
                UUID.fromString(user),
                org,
                role);
        return org;
    }

    private void cleanup(UUID org) {
        jdbc.update("DELETE FROM answerbase.workspaces WHERE organization_id=?", org);
        jdbc.update("DELETE FROM answerbase.users WHERE organization_id=?", org);
        jdbc.update("DELETE FROM answerbase.organizations WHERE id=?", org);
    }

    private HttpResponse<String> send(String method, String path, String jwt, String body)
            throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request =
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + jwt)
                            .method(
                                    method,
                                    body == null
                                            ? HttpRequest.BodyPublishers.noBody()
                                            : HttpRequest.BodyPublishers.ofString(body));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void organizationEnrollmentIsAtomicAndSingleTenant() throws Exception {
        String user = UUID.randomUUID().toString();
        String jwt = userToken(user);
        UUID org = null;
        try {
            assertThat(
                            create(jwt, UUID.randomUUID().toString(), "{\"name\":\"Denied\"}")
                                    .statusCode())
                    .isEqualTo(403);
            assertThat(send("POST", "/api/v1/organizations", jwt, "{\"name\":\" \"}").statusCode())
                    .isEqualTo(400);
            try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var first =
                        pool.submit(
                                () ->
                                        send(
                                                "POST",
                                                "/api/v1/organizations",
                                                jwt,
                                                "{\"name\":\"Acme\"}"));
                var second =
                        pool.submit(
                                () ->
                                        send(
                                                "POST",
                                                "/api/v1/organizations",
                                                jwt,
                                                "{\"name\":\"Acme\"}"));
                var a = first.get();
                var b = second.get();
                assertThat(a.statusCode()).isEqualTo(200);
                assertThat(b.statusCode()).isEqualTo(200);
                assertThat(a.body()).isEqualTo(b.body());
                org =
                        UUID.fromString(
                                tools.jackson.databind.json.JsonMapper.builder()
                                        .build()
                                        .readTree(a.body())
                                        .path("id")
                                        .asText());
            }
            assertThat(get("/api/v1/me", jwt).body()).contains(org.toString(), "owner");
            assertThat(
                            send("POST", "/api/v1/organizations", jwt, "{\"name\":\"Globex\"}")
                                    .statusCode())
                    .isEqualTo(409);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM answerbase.users WHERE id=?",
                                    Integer.class,
                                    UUID.fromString(user)))
                    .isEqualTo(1);
        } finally {
            if (org != null) cleanup(org);
        }
    }

    @Test
    void organizationAndWorkspaceRolesAreIndependentAndDatabaseRejectsCrossTenantMembership()
            throws Exception {
        String owner = UUID.randomUUID().toString();
        String member = UUID.randomUUID().toString();
        String stranger = UUID.randomUUID().toString();
        String companyAdmin = UUID.randomUUID().toString();
        UUID org = provision(owner, "owner", null);
        provision(member, "member", org);
        provision(companyAdmin, "admin", org);
        UUID otherOrg = provision(stranger, "owner", null);
        String jwt = userToken(owner);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        try {
            String a =
                    mapper.readTree(
                                    create(
                                                    jwt,
                                                    UUID.randomUUID().toString(),
                                                    "{\"name\":\"Support\"}")
                                            .body())
                            .path("id")
                            .asText();
            String b =
                    mapper.readTree(
                                    create(
                                                    jwt,
                                                    UUID.randomUUID().toString(),
                                                    "{\"name\":\"Engineering\"}")
                                            .body())
                            .path("id")
                            .asText();
            String c =
                    mapper.readTree(
                                    create(
                                                    jwt,
                                                    UUID.randomUUID().toString(),
                                                    "{\"name\":\"Private\"}")
                                            .body())
                            .path("id")
                            .asText();
            String membersA = "/api/v1/workspaces/" + a + "/members/";
            assertThat(
                            create(
                                            userToken(member),
                                            UUID.randomUUID().toString(),
                                            "{\"name\":\"Denied\"}")
                                    .statusCode())
                    .isEqualTo(403);
            assertThat(get("/api/v1/workspaces/" + a, userToken(companyAdmin)).statusCode())
                    .isEqualTo(404);
            assertThat(send("PUT", membersA + member, jwt, "{\"role\":\"viewer\"}").statusCode())
                    .isEqualTo(204);
            assertThat(
                            send(
                                            "PUT",
                                            "/api/v1/workspaces/" + b + "/members/" + member,
                                            jwt,
                                            "{\"role\":\"editor\"}")
                                    .statusCode())
                    .isEqualTo(204);
            assertThat(get("/api/v1/workspaces/" + a, userToken(member)).body()).contains("viewer");
            assertThat(get("/api/v1/workspaces/" + b, userToken(member)).body()).contains("editor");
            assertThat(get("/api/v1/workspaces/" + c, userToken(member)).statusCode())
                    .isEqualTo(404);
            assertThat(get("/api/v1/workspaces", userToken(member)).body())
                    .contains(a, b)
                    .doesNotContain(c);
            assertThat(
                            send(
                                            "PUT",
                                            membersA + member,
                                            userToken(member),
                                            "{\"role\":\"admin\"}")
                                    .statusCode())
                    .isEqualTo(403);
            assertThat(send("PUT", membersA + stranger, jwt, "{\"role\":\"viewer\"}").statusCode())
                    .isEqualTo(404);
            assertThat(send("PUT", membersA + owner, jwt, "{\"role\":\"viewer\"}").statusCode())
                    .isEqualTo(409);
            assertThat(send("DELETE", membersA + owner, jwt, null).statusCode()).isEqualTo(409);
            assertThat(
                            send("PUT", membersA + member, jwt, "{\"role\":\"superadmin\"}")
                                    .statusCode())
                    .isEqualTo(400);
            assertThatThrownBy(
                            () ->
                                    jdbc.update(
                                            "INSERT INTO answerbase.workspace_members (organization_id,workspace_id,user_id,role) VALUES (?,?,?,'viewer')",
                                            org,
                                            UUID.fromString(a),
                                            UUID.fromString(stranger)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(
                            () ->
                                    jdbc.update(
                                            "INSERT INTO answerbase.workspace_members (organization_id,workspace_id,user_id,role) VALUES (?,?,?,'viewer')",
                                            otherOrg,
                                            UUID.fromString(a),
                                            UUID.fromString(stranger)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(send("DELETE", membersA + member, jwt, null).statusCode()).isEqualTo(204);
            assertThat(get("/api/v1/workspaces/" + a, userToken(member)).statusCode())
                    .isEqualTo(404);
            assertThat(get("/api/v1/workspaces/" + b, userToken(member)).statusCode())
                    .isEqualTo(200);
        } finally {
            cleanup(org);
            cleanup(otherOrg);
        }
    }

    private HttpResponse<String> delegated(String method, String path, String grant, String body)
            throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            "http://localhost:"
                                                    + port
                                                    + "/api/v1/delegated"
                                                    + path))
                            .header("Content-Type", "application/json")
                            .header("X-Support-Grant", grant)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .method(
                                    method,
                                    body == null
                                            ? HttpRequest.BodyPublishers.noBody()
                                            : HttpRequest.BodyPublishers.ofString(body));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void staffAndDelegationAreScopedRevocableAndAudited() throws Exception {
        String employee = UUID.randomUUID().toString();
        String secondEmployee = UUID.randomUUID().toString();
        String owner = UUID.randomUUID().toString();
        String member = UUID.randomUUID().toString();
        String outsider = UUID.randomUUID().toString();
        UUID company = provision(owner, "owner", null);
        provision(member, "member", company);
        UUID otherCompany = provision(outsider, "owner", null);
        jdbc.update(
                "INSERT INTO answerbase.staff_access(user_id,role) VALUES (?,'support'),(?,'platform_admin')",
                UUID.fromString(employee),
                UUID.fromString(secondEmployee));
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        String staffJwt = userToken(employee);
        UUID session = null;
        try {
            assertThat(get("/api/v1/support/me", userToken(owner)).statusCode()).isEqualTo(403);
            assertThat(get("/api/v1/support/me", staffJwt).statusCode()).isEqualTo(200);
            assertThat(get("/api/v1/workspaces", staffJwt).statusCode()).isEqualTo(403);
            assertThat(
                            send(
                                            "POST",
                                            "/api/v1/organizations",
                                            staffJwt,
                                            "{\"name\":\"Forbidden\"}")
                                    .statusCode())
                    .isEqualTo(403);
            assertThatThrownBy(
                            () ->
                                    jdbc.update(
                                            "INSERT INTO answerbase.staff_access(user_id,role) VALUES (?,'support')",
                                            UUID.fromString(owner)))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThatThrownBy(() -> provision(employee, "member", company))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            String space =
                    mapper.readTree(
                                    create(
                                                    userToken(owner),
                                                    UUID.randomUUID().toString(),
                                                    "{\"name\":\"Support test\"}")
                                            .body())
                            .path("id")
                            .asText();
            var start =
                    send(
                            "POST",
                            "/api/v1/support/sessions",
                            staffJwt,
                            "{\"organizationId\":\""
                                    + company
                                    + "\",\"reason\":\"SUP-123 investigate access\"}");
            assertThat(start.statusCode()).isEqualTo(200);
            session = UUID.fromString(mapper.readTree(start.body()).path("id").asText());
            String path = "/api/v1/support/sessions/" + session;
            assertThat(get(path, userToken(secondEmployee)).statusCode()).isEqualTo(404);
            assertThat(
                            send(
                                            "POST",
                                            path + "/delegations",
                                            staffJwt,
                                            "{\"userId\":\"" + outsider + "\"}")
                                    .statusCode())
                    .isEqualTo(404);
            var issued =
                    send(
                            "POST",
                            path + "/delegations",
                            staffJwt,
                            "{\"userId\":\"" + member + "\"}");
            assertThat(issued.statusCode()).isEqualTo(200);
            String grant = mapper.readTree(issued.body()).path("token").asText();
            assertThat(delegated("GET", "/workspaces", grant, null).body()).isEqualTo("[]");
            assertThat(delegated("GET", "/workspaces/" + space, grant, null).statusCode())
                    .isEqualTo(404);
            assertThat(
                            delegated("POST", "/workspaces", grant, "{\"name\":\"Forbidden\"}")
                                    .statusCode())
                    .isEqualTo(403);
            assertThat(get("/api/v1/support/me", grant).statusCode()).isEqualTo(401);
            assertThat(
                            delegated("GET", "/workspaces/" + space + "/documents", grant, null)
                                    .statusCode())
                    .isEqualTo(404);
            String repair = path + "/workspaces/" + space + "/members/" + member;
            assertThat(send("PUT", repair, staffJwt, "{\"role\":\"viewer\"}").statusCode())
                    .isEqualTo(204);
            assertThat(delegated("GET", "/workspaces", grant, null).body())
                    .contains(space, "viewer");
            assertThat(
                            send(
                                            "PUT",
                                            path + "/workspaces/" + space + "/members/" + outsider,
                                            staffJwt,
                                            "{\"role\":\"viewer\"}")
                                    .statusCode())
                    .isEqualTo(404);
            assertThat(
                            delegated("GET", "/workspaces/" + space + "/documents", grant, null)
                                    .statusCode())
                    .isEqualTo(200);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM answerbase.support_audit WHERE actor_id=? AND target_user_id=? AND action='delegated.document.list'",
                                    Integer.class,
                                    UUID.fromString(employee),
                                    UUID.fromString(member)))
                    .isEqualTo(1);
            String replacement =
                    mapper.readTree(
                                    send(
                                                    "POST",
                                                    path + "/delegations",
                                                    staffJwt,
                                                    "{\"userId\":\"" + owner + "\"}")
                                            .body())
                            .path("token")
                            .asText();
            assertThat(delegated("GET", "/me", grant, null).statusCode()).isEqualTo(401);
            assertThat(
                            delegated("GET", "/workspaces/" + space + "/documents", grant, null)
                                    .statusCode())
                    .isEqualTo(401);
            assertThat(
                            delegated(
                                            "POST",
                                            "/workspaces",
                                            replacement,
                                            "{\"name\":\"Created on behalf\"}")
                                    .statusCode())
                    .isEqualTo(201);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM answerbase.support_audit WHERE actor_id=? AND target_user_id=? AND action='delegated.workspace.create'",
                                    Integer.class,
                                    UUID.fromString(employee),
                                    UUID.fromString(owner)))
                    .isEqualTo(1);
            jdbc.update(
                    "UPDATE answerbase.staff_access SET active=false WHERE user_id=?",
                    UUID.fromString(employee));
            assertThat(delegated("GET", "/me", replacement, null).statusCode()).isEqualTo(401);
            assertThat(get(path, staffJwt).statusCode()).isEqualTo(403);
            jdbc.update(
                    "UPDATE answerbase.staff_access SET active=true WHERE user_id=?",
                    UUID.fromString(employee));
            jdbc.update(
                    "UPDATE answerbase.support_grants SET expires_at=now()-interval '1 minute' WHERE session_id=?",
                    session);
            assertThat(delegated("GET", "/me", replacement, null).statusCode()).isEqualTo(401);
            String finalGrant =
                    mapper.readTree(
                                    send(
                                                    "POST",
                                                    path + "/delegations",
                                                    staffJwt,
                                                    "{\"userId\":\"" + owner + "\"}")
                                            .body())
                            .path("token")
                            .asText();
            assertThat(send("DELETE", path, staffJwt, null).statusCode()).isEqualTo(204);
            assertThat(delegated("GET", "/me", finalGrant, null).statusCode()).isEqualTo(401);
            assertThat(
                            send(
                                            "POST",
                                            path + "/delegations",
                                            staffJwt,
                                            "{\"userId\":\"" + owner + "\"}")
                                    .statusCode())
                    .isEqualTo(404);
            assertThatThrownBy(
                            () ->
                                    jdbc.update(
                                            "DELETE FROM answerbase.support_audit WHERE actor_id=?",
                                            UUID.fromString(employee)))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {
            if (session != null) {
                jdbc.update("DELETE FROM answerbase.support_grants WHERE session_id=?", session);
                jdbc.update("DELETE FROM answerbase.support_sessions WHERE id=?", session);
            }
            jdbc.update(
                    "DELETE FROM answerbase.staff_access WHERE user_id IN (?,?)",
                    UUID.fromString(employee),
                    UUID.fromString(secondEmployee));
            cleanup(company);
            cleanup(otherCompany);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired DocumentService documents;
    @org.springframework.beans.factory.annotation.Autowired DocumentAccessService documentAccess;
    @org.springframework.beans.factory.annotation.Autowired WorkspaceService workspaceService;

    @org.springframework.beans.factory.annotation.Autowired
    com.answerbase.core.documents.DocumentStorage documentStorage;

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void documentsArePrivateVersionedRetryableAndProcessedWithSourceLocations() throws Exception {
        String owner = UUID.randomUUID().toString(),
                viewer = UUID.randomUUID().toString(),
                outsider = UUID.randomUUID().toString();
        UUID org = provision(owner, "owner", null), otherOrg = provision(outsider, "owner", null);
        provision(viewer, "member", org);
        UUID workspace =
                workspaceService
                        .create(UUID.fromString(owner), UUID.randomUUID(), "Documents")
                        .workspace()
                        .id();
        UUID otherWorkspace =
                workspaceService
                        .create(UUID.fromString(owner), UUID.randomUUID(), "Private team")
                        .workspace()
                        .id();
        jdbc.update(
                "INSERT INTO answerbase.workspace_members(organization_id,workspace_id,user_id,role) VALUES (?,?,?,'viewer')",
                org,
                workspace,
                UUID.fromString(viewer));
        String base = "/api/v1/workspaces/" + workspace + "/documents";
        String jwt = userToken(owner);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var processor =
                new com.answerbase.core.documents.IngestionProcessor(
                        jdbc,
                        new org.springframework.transaction.support.TransactionTemplate(
                                transactionManager),
                        documentStorage);
        try {
            UUID key = UUID.randomUUID();
            assertThat(
                            upload(
                                            base,
                                            userToken(viewer),
                                            key,
                                            "policy.txt",
                                            "Private policy\nRefunds within 30 days.")
                                    .statusCode())
                    .isEqualTo(403);
            assertThat(
                            upload(base, userToken(outsider), key, "policy.txt", "Private policy")
                                    .statusCode())
                    .isEqualTo(404);
            var first =
                    upload(base, jwt, key, "policy.txt", "Private policy\nRefunds within 30 days.");
            assertThat(first.statusCode()).isEqualTo(202);
            UUID version = UUID.fromString(mapper.readTree(first.body()).path("id").asText());
            UUID document =
                    UUID.fromString(mapper.readTree(first.body()).path("documentId").asText());
            assertThat(
                            upload(
                                            base,
                                            jwt,
                                            key,
                                            "policy.txt",
                                            "Private policy\nRefunds within 30 days.")
                                    .body())
                    .isEqualTo(first.body());
            assertThat(upload(base, jwt, key, "policy.txt", "changed").statusCode()).isEqualTo(409);
            assertThat(upload(base, jwt, UUID.randomUUID(), "fake.pdf", "not pdf").statusCode())
                    .isEqualTo(400);
            String versionPath = base + "/" + document + "/versions/" + version;
            assertThat(get(versionPath + "/chunks", jwt).statusCode()).isEqualTo(409);
            assertThat(get(versionPath + "/original", userToken(viewer)).body())
                    .isEqualTo("Private policy\nRefunds within 30 days.");
            assertThat(get(versionPath + "/original", userToken(outsider)).statusCode())
                    .isEqualTo(404);
            assertThat(
                            get(
                                            versionPath.replace(
                                                            workspace.toString(),
                                                            otherWorkspace.toString())
                                                    + "/original",
                                            jwt)
                                    .statusCode())
                    .isEqualTo(404);
            assertThat(processor.processOne()).isTrue();
            assertThat(get(versionPath + "/chunks", jwt).body())
                    .contains("Refunds within 30 days.", "startLine", "endLine");
            assertThat(get(versionPath + "/chunks", userToken(outsider)).statusCode())
                    .isEqualTo(404);
            assertThat(get(versionPath + "/original", null).statusCode()).isEqualTo(401);
            assertThatThrownBy(
                            () ->
                                    jdbc.update(
                                            "INSERT INTO answerbase.document_chunks(organization_id,workspace_id,version_id,ordinal,content) VALUES (?,?,?,?,?)",
                                            otherOrg,
                                            workspace,
                                            version,
                                            999,
                                            "leak"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            var unchanged =
                    upload(
                            base + "/" + document + "/versions",
                            jwt,
                            UUID.randomUUID(),
                            "policy.txt",
                            "Private policy\nRefunds within 30 days.");
            assertThat(mapper.readTree(unchanged.body()).path("id").asText())
                    .isEqualTo(version.toString());
            assertThat(
                            upload(
                                            base,
                                            jwt,
                                            UUID.randomUUID(),
                                            "copy.txt",
                                            "Private policy\nRefunds within 30 days.")
                                    .statusCode())
                    .isEqualTo(202);
            assertThat(processor.processOne()).isTrue();
            assertThat(get(base + "/duplicates", jwt).body()).contains("identical", "copy.txt");
            assertThat(get(base + "/duplicates", userToken(viewer)).statusCode()).isEqualTo(403);
            assertThat(get(versionPath + "/chunks", jwt).body())
                    .contains("startOffset", "endOffset", "sectionPath");
            var group = documentAccess.create(UUID.fromString(owner), workspace, "HR");
            documentAccess.policy(
                    UUID.fromString(owner),
                    workspace,
                    document,
                    new DocumentAccessService.Policy(true, java.util.List.of(group.id())));
            assertThat(get(versionPath + "/original", userToken(viewer)).statusCode())
                    .isEqualTo(404);
            assertThat(get(versionPath + "/chunks", userToken(viewer)).statusCode()).isEqualTo(404);
            assertThat(get(base, userToken(viewer)).body()).doesNotContain(document.toString());
            assertThat(get(versionPath + "/original", jwt).statusCode()).isEqualTo(200);
            assertThat(
                            get("/api/v1/workspaces/" + workspace + "/groups", userToken(viewer))
                                    .statusCode())
                    .isEqualTo(403);
            assertThatThrownBy(
                            () ->
                                    documentAccess.members(
                                            UUID.fromString(owner),
                                            workspace,
                                            group.id(),
                                            java.util.List.of(UUID.fromString(outsider))))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            documentAccess.members(
                    UUID.fromString(owner),
                    workspace,
                    group.id(),
                    java.util.List.of(UUID.fromString(viewer)));
            assertThat(get(versionPath + "/chunks", userToken(viewer)).statusCode()).isEqualTo(200);
            documentAccess.members(
                    UUID.fromString(owner), workspace, group.id(), java.util.List.of());
            assertThat(get(versionPath + "/chunks", userToken(viewer)).statusCode()).isEqualTo(404);
            documentAccess.policy(
                    UUID.fromString(owner),
                    workspace,
                    document,
                    new DocumentAccessService.Policy(false, java.util.List.of()));
            // A malformed replacement fails without replacing the usable version.
            var next =
                    upload(
                            base + "/" + document + "/versions",
                            jwt,
                            UUID.randomUUID(),
                            "broken.pdf",
                            "%PDF-broken");
            assertThat(next.statusCode()).isEqualTo(202);
            UUID failed = UUID.fromString(mapper.readTree(next.body()).path("id").asText());
            for (int attempt = 0; attempt < 3; attempt++) {
                jdbc.update(
                        "UPDATE answerbase.ingestion_jobs SET available_at=now()-interval '1 minute' WHERE version_id=?",
                        failed);
                assertThat(processor.processOne()).isTrue();
            }
            assertThat(get(base, jwt).body())
                    .contains("failed", "\"processedVersionId\":\"" + version + "\"");
            String retry = base + "/" + document + "/versions/" + failed + "/retry";
            assertThat(send("POST", retry, userToken(viewer), null).statusCode()).isEqualTo(403);
            assertThat(send("POST", retry, jwt, null).statusCode()).isEqualTo(204);
            assertThat(send("POST", retry, jwt, null).statusCode()).isEqualTo(409);
            // A dead worker's lease can be reclaimed and is capped by the attempt limit.
            jdbc.update(
                    "UPDATE answerbase.ingestion_jobs SET state='processing',lease_token=?,lease_until=now()-interval '1 minute',attempts=3 WHERE version_id=?",
                    UUID.randomUUID(),
                    failed);
            assertThat(processor.processOne()).isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT state FROM answerbase.ingestion_jobs WHERE version_id=?",
                                    String.class,
                                    failed))
                    .isEqualTo("failed");
            // Permission revocation takes effect for original files as well as listings.
            jdbc.update(
                    "DELETE FROM answerbase.workspace_members WHERE user_id=?",
                    UUID.fromString(viewer));
            assertThat(get(versionPath + "/original", userToken(viewer)).statusCode())
                    .isEqualTo(404);
        } finally {
            for (var row :
                    jdbc.queryForList(
                            "SELECT storage_key FROM answerbase.document_versions WHERE organization_id=?",
                            org)) documentStorage.delete((String) row.get("storage_key"));
            jdbc.update(
                    "DELETE FROM answerbase.document_group_access WHERE organization_id=?", org);
            jdbc.update("DELETE FROM answerbase.group_members WHERE organization_id=?", org);
            jdbc.update("DELETE FROM answerbase.document_groups WHERE organization_id=?", org);
            jdbc.update("DELETE FROM answerbase.document_chunks WHERE organization_id=?", org);
            jdbc.update(
                    "DELETE FROM answerbase.ingestion_jobs WHERE version_id IN (SELECT id FROM answerbase.document_versions WHERE organization_id=?)",
                    org);
            jdbc.update("DELETE FROM answerbase.document_versions WHERE organization_id=?", org);
            jdbc.update("DELETE FROM answerbase.documents WHERE organization_id=?", org);
            cleanup(org);
            cleanup(otherOrg);
        }
    }

    private HttpResponse<String> upload(
            String path, String jwt, UUID key, String filename, String content) throws Exception {
        String boundary = "AnswerbaseTestBoundary";
        String body =
                "--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                        + filename
                        + "\"\r\nContent-Type: application/octet-stream\r\n\r\n"
                        + content
                        + "\r\n--"
                        + boundary
                        + "--\r\n";
        try (var client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .header("Authorization", "Bearer " + jwt)
                            .header("Idempotency-Key", key.toString())
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private String token(
            String issuer, String audience, String role, String subject, Instant expiry, RSAKey key)
            throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(audience)
                        .subject(subject)
                        .claim("role", role)
                        .claim("is_anonymous", false)
                        .claim("email", "person@example.test")
                        .issueTime(Date.from(Instant.now().minusSeconds(180)))
                        .expirationTime(Date.from(expiry))
                        .build();
        var jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256)
                                .type(JOSEObjectType.JWT)
                                .keyID(key.getKeyID())
                                .build(),
                        claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request =
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
            if (token != null) request.header("Authorization", "Bearer " + token);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
