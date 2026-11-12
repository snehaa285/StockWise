package com.stockwise.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.domain.AuthSession;
import com.stockwise.auth.domain.AuthenticationMethod;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.auth.persistence.AuthLoginEventRepository;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class AuthSessionManagementIntegrationTest {
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://[^/:?]+(?::\\d+)?/(?<schema>[^?;/]+)(?:\\?.*)?$");
    private static final String TEST_USERNAME = "stockwise_auth_test_user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

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
                    "Session integration tests require the dedicated stockwise_auth_test datasource.");
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
                    authSessionRepository.findAllByUser_Id(user.getId()).forEach(session ->
                            authRefreshTokenRepository.deleteAllBySessionId(session.getId()));
                    authSessionRepository.deleteAllByUserId(user.getId());
                    appUserRepository.deleteByEmailNormalized(email);
                }));
    }

    @Test
    void activeSessionsListsCurrentAndOtherSessionsWithUserAgent() throws Exception {
        Login session1 = loginNewUser("Browser-Chrome-1");
        Login session2 = loginExistingUser(session1.email(), "Browser-Firefox-2");

        MvcResult result = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session2.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        List<AuthSessionResponse> sessions = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AuthSessionResponse.class));

        assertThat(sessions).hasSize(2);
        AuthSessionResponse current = sessions.stream().filter(AuthSessionResponse::current).findFirst().orElseThrow();
        AuthSessionResponse other = sessions.stream().filter(s -> !s.current()).findFirst().orElseThrow();

        assertThat(current.userAgent()).isEqualTo("Browser-Firefox-2");
        assertThat(current.authenticationMethod()).isEqualTo("EMAIL_PASSWORD");
        assertThat(other.userAgent()).isEqualTo("Browser-Chrome-1");
    }

    @Test
    void revokeIndividualSessionTerminatesItAndLeavesOtherActive() throws Exception {
        Login session1 = loginNewUser("Device-1");
        Login session2 = loginExistingUser(session1.email(), "Device-2");

        // Session 2 revokes session 1
        mockMvc.perform(delete("/api/v1/auth/sessions/" + session1.sessionId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session2.accessToken()))
                .andExpect(status().isNoContent());

        // Session 1 is now revoked
        AuthSession revokedSession = authSessionRepository.findById(session1.sessionId()).orElseThrow();
        assertThat(revokedSession.getRevokedAt()).isNotNull();

        // Listing sessions for session 2 shows only 1 active session
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session2.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(session2.sessionId().toString()))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void revokeCurrentSessionClearsCookie() throws Exception {
        Login session = loginNewUser("Device-Current");

        MvcResult result = mockMvc.perform(delete("/api/v1/auth/sessions/" + session.sessionId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken()))
                .andExpect(status().isNoContent())
                .andReturn();

        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie).contains("Max-Age=0");
    }

    @Test
    void revokeOthersRevokesAllExceptCurrent() throws Exception {
        Login session1 = loginNewUser("Device-A");
        Login session2 = loginExistingUser(session1.email(), "Device-B");
        Login session3 = loginExistingUser(session1.email(), "Device-C");

        // Session 3 calls revoke-others
        mockMvc.perform(post("/api/v1/auth/sessions/revoke-others")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session3.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedCount").value(2));

        // Listing sessions from session 3 shows only session 3 active
        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session3.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(session3.sessionId().toString()));
    }

    @Test
    void loginHistoryRecordsSuccessFailureAndRevocationIsolatedPerUser() throws Exception {
        Login user1 = loginNewUser("User1-Agent");
        Login user2 = loginNewUser("User2-Agent");

        // Trigger a failed login for user 1
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "User1-Bad-Password-Agent")
                        .content(objectMapper.writeValueAsString(new LoginRequest(user1.email(), "WrongPassword123!"))))
                .andExpect(status().isUnauthorized());

        // Check user 1's login history
        MvcResult history1 = mockMvc.perform(get("/api/v1/auth/login-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user1.accessToken()))
                .andExpect(status().isOk())
                .andReturn();

        List<AuthLoginHistoryResponse> events1 = objectMapper.readValue(
                history1.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AuthLoginHistoryResponse.class));

        assertThat(events1).isNotEmpty();
        assertThat(events1).anyMatch(e -> e.eventType().name().equals("LOGIN_FAILURE") && !e.successful());
        assertThat(events1).anyMatch(e -> e.eventType().name().equals("LOGIN_SUCCESS") && e.successful());

        // None of user 2's events should appear in user 1's history
        for (AuthLoginHistoryResponse event : events1) {
            assertThat(event.userAgent()).doesNotContain("User2-Agent");
        }

        // Check user 2's login history
        MvcResult history2 = mockMvc.perform(get("/api/v1/auth/login-history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user2.accessToken()))
                .andExpect(status().isOk())
                .andReturn();

        List<AuthLoginHistoryResponse> events2 = objectMapper.readValue(
                history2.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, AuthLoginHistoryResponse.class));

        assertThat(events2).isNotEmpty();
        for (AuthLoginHistoryResponse event : events2) {
            assertThat(event.userAgent()).doesNotContain("User1-Agent");
        }
    }

    @Test
    void cannotRevokeAnotherUsersSession() throws Exception {
        Login user1 = loginNewUser("User1-Agent");
        Login user2 = loginNewUser("User2-Agent");

        // User 2 tries to delete User 1's session
        mockMvc.perform(delete("/api/v1/auth/sessions/" + user1.sessionId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user2.accessToken()))
                .andExpect(status().isNotFound());

        // User 1's session remains active
        AuthSession session1 = authSessionRepository.findById(user1.sessionId()).orElseThrow();
        assertThat(session1.getRevokedAt()).isNull();
    }

    private Login loginNewUser(String userAgent) throws Exception {
        String email = "session-test-" + UUID.randomUUID() + "@example.com";
        testEmails.add(email);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Test User", email, "ValidPass123!"))))
                .andExpect(status().isCreated());

        return loginExistingUser(email, userAgent);
    }

    private Login loginExistingUser(String email, String userAgent) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", userAgent)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "ValidPass123!"))))
                .andExpect(status().isOk())
                .andReturn();

        String body = loginResult.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(body).get("accessToken").asText();

        // Extract session ID from the user's active sessions in DB
        AppUser user = appUserRepository.findByEmailNormalized(email).orElseThrow();
        List<AuthSession> sessions = authSessionRepository.findAllByUser_Id(user.getId());
        AuthSession latest = sessions.stream()
                .filter(s -> userAgent.equals(s.getUserAgent()))
                .reduce((first, second) -> second)
                .orElseThrow();

        return new Login(user.getId(), email, latest.getId(), accessToken);
    }

    private record Login(UUID userId, String email, UUID sessionId, String accessToken) {
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Test requires " + name);
        }
        return value;
    }
}

