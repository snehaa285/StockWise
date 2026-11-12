package com.stockwise.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.auth.application.FirebaseTokenVerificationException;
import com.stockwise.auth.application.FirebaseTokenVerifier;
import com.stockwise.auth.application.VerifiedFirebaseIdentity;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.domain.AuthLoginEvent;
import com.stockwise.auth.domain.AuthenticationMethod;
import com.stockwise.auth.domain.FirebaseIdentity;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.auth.persistence.AuthLoginEventRepository;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import com.stockwise.auth.persistence.FirebaseIdentityRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "stockwise.auth.refresh-cookie.secure=false",
        "stockwise.auth.refresh-cookie.same-site=Lax"
})
class FirebaseAuthenticationIntegrationTest {
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://[^/:?]+(?::\\d+)?/(?<schema>[^?;/]+)(?:\\?.*)?$");
    private static final String TEST_USERNAME = "stockwise_auth_test_user";

    @TestConfiguration
    static class TestFirebaseConfig {
        @Bean
        @Primary
        FirebaseTokenVerifier mockFirebaseTokenVerifier() {
            return idToken -> {
                if ("valid-token-new".equals(idToken)) {
                    return new VerifiedFirebaseIdentity(
                            "google-uid-new-123",
                            "new-federated@example.com",
                            true,
                            "Google User");
                }
                if ("valid-token-existing-email".equals(idToken)) {
                    return new VerifiedFirebaseIdentity(
                            "google-uid-different-456",
                            "password-user@example.com",
                            true,
                            "Google Linker");
                }
                if ("valid-token-mismatched-email".equals(idToken)) {
                    return new VerifiedFirebaseIdentity(
                            "google-uid-other-789",
                            "other-email@example.com",
                            true,
                            "Google Other");
                }
                if ("unverified-email-token".equals(idToken)) {
                    return new VerifiedFirebaseIdentity(
                            "google-uid-unverified",
                            "unverified@example.com",
                            false,
                            "Unverified");
                }
                throw new FirebaseTokenVerificationException();
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private FirebaseIdentityRepository firebaseIdentityRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    @Autowired
    private AuthLoginEventRepository authLoginEventRepository;

    private final List<String> testEmails = new java.util.concurrent.CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void configureIsolatedTestDatasource(DynamicPropertyRegistry properties) {
        String url = requiredEnvironmentVariable("AUTH_TEST_DB_URL");
        String username = requiredEnvironmentVariable("AUTH_TEST_DB_USERNAME");
        String password = requiredEnvironmentVariable("AUTH_TEST_DB_PASSWORD");
        Matcher matcher = MYSQL_URL.matcher(url);
        if (!matcher.matches()
                || !"stockwise_auth_test".equals(matcher.group("schema"))
                || !TEST_USERNAME.equals(username)) {
            throw new IllegalStateException(
                    "Firebase integration tests require the dedicated stockwise_auth_test datasource.");
        }

        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> username);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.enabled", () -> false);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        byte[] csrfKey = new byte[32];
        new SecureRandom().nextBytes(csrfKey);
        properties.add("stockwise.auth.csrf.secret",
                () -> Base64.getEncoder().encodeToString(csrfKey));
    }

    @AfterEach
    void cleanup() {
        testEmails.forEach(email -> appUserRepository.findByEmailNormalized(email)
                .ifPresent(user -> {
                    authLoginEventRepository.deleteAllByUserId(user.getId());
                    firebaseIdentityRepository.findByUser_Id(user.getId())
                            .ifPresent(firebaseIdentityRepository::delete);
                    authSessionRepository.findAllByUser_Id(user.getId()).forEach(session ->
                            authRefreshTokenRepository.deleteAllBySessionId(session.getId()));
                    authSessionRepository.deleteAllByUserId(user.getId());
                    appUserRepository.deleteByEmailNormalized(email);
                }));
    }

    @Test
    void firebaseLoginCreatesNewUserAndSessionWithAudit() throws Exception {
        testEmails.add("new-federated@example.com");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Firebase-Test-Agent")
                        .content(objectMapper.writeValueAsString(new FirebaseIdTokenRequest("valid-token-new"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.user.email").value("new-federated@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Google User"))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("stockwise_refresh=");

        // Verify user was created with null password
        AppUser user = appUserRepository.findByEmailNormalized("new-federated@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNull();

        // Verify firebase identity linked
        FirebaseIdentity identity = firebaseIdentityRepository.findByFirebaseUid("google-uid-new-123").orElseThrow();
        assertThat(identity.getUser().getId()).isEqualTo(user.getId());

        // Verify session has GOOGLE method
        var sessions = authSessionRepository.findAllByUser_Id(user.getId());
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getAuthenticationMethod()).isEqualTo(AuthenticationMethod.GOOGLE);

        // Verify audit event
        List<AuthLoginEvent> events = authLoginEventRepository.findTop100ByUser_IdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAuthenticationMethod()).isEqualTo(AuthenticationMethod.GOOGLE);
        assertThat(events.get(0).getUserAgent()).isEqualTo("Firebase-Test-Agent");
    }

    @Test
    void firebaseLoginWithExistingLinkedIdentityAuthenticatesSuccessfully() throws Exception {
        testEmails.add("new-federated@example.com");

        // First login creates user
        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "First-Device")
                        .content(objectMapper.writeValueAsString(new FirebaseIdTokenRequest("valid-token-new"))))
                .andExpect(status().isOk());

        // Second login uses existing identity
        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Second-Device")
                        .content(objectMapper.writeValueAsString(new FirebaseIdTokenRequest("valid-token-new"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("new-federated@example.com"));

        AppUser user = appUserRepository.findByEmailNormalized("new-federated@example.com").orElseThrow();
        var sessions = authSessionRepository.findAllByUser_Id(user.getId());
        assertThat(sessions).hasSize(2);
    }

    @Test
    void firebaseLoginWithUnlinkedExistingEmailRejectsWithConflict() throws Exception {
        testEmails.add("password-user@example.com");

        // Register a password user with the same email
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Password User", "password-user@example.com", "Password123!"))))
                .andExpect(status().isCreated());

        // Attempt Firebase login with the same email without prior linking
        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Attacker-Agent")
                        .content(objectMapper.writeValueAsString(
                                new FirebaseIdTokenRequest("valid-token-existing-email"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Account conflict"));

        // Audit failure should be recorded
        AppUser user = appUserRepository.findByEmailNormalized("password-user@example.com").orElseThrow();
        List<AuthLoginEvent> events = authLoginEventRepository.findTop100ByUser_IdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType().name()).isEqualTo("LOGIN_FAILURE");
    }

    @Test
    void linkFirebaseIdentityAllowsSubsequentFirebaseLogin() throws Exception {
        testEmails.add("password-user@example.com");

        // Register and login with password
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Password User", "password-user@example.com", "Password123!"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("password-user@example.com", "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Link Firebase identity
        mockMvc.perform(post("/api/v1/auth/firebase/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("User-Agent", "Link-Agent")
                        .content(objectMapper.writeValueAsString(
                                new FirebaseIdTokenRequest("valid-token-existing-email"))))
                .andExpect(status().isNoContent());

        // Now Firebase login should succeed!
        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FirebaseIdTokenRequest("valid-token-existing-email"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("password-user@example.com"));
    }

    @Test
    void linkWithMismatchedEmailRejects() throws Exception {
        testEmails.add("password-user@example.com");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Password User", "password-user@example.com", "Password123!"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("password-user@example.com", "Password123!"))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Try to link token with different email
        mockMvc.perform(post("/api/v1/auth/firebase/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .content(objectMapper.writeValueAsString(
                                new FirebaseIdTokenRequest("valid-token-mismatched-email"))))
                .andExpect(status().isConflict());
    }

    @Test
    void unverifiedEmailOrInvalidTokenRejectsWithUnauthorized() throws Exception {
        // Unverified email
        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FirebaseIdTokenRequest("unverified-email-token"))))
                .andExpect(status().isUnauthorized());

        // Invalid token
        mockMvc.perform(post("/api/v1/auth/firebase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new FirebaseIdTokenRequest("totally-bogus-token"))))
                .andExpect(status().isUnauthorized());
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Test requires " + name);
        }
        return value;
    }
}
