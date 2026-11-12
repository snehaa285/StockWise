package com.stockwise.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.auth.application.RefreshTokenFactory;
import com.stockwise.auth.domain.AuthRefreshToken;
import com.stockwise.auth.domain.AuthSession;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthLoginEventRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "stockwise.auth.refresh-cookie.secure=false",
        "stockwise.auth.refresh-cookie.same-site=Lax"
})
class AuthSessionLifecycleIntegrationTest {
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://[^/:?]+(?::\\d+)?/(?<schema>[^?;/]+)(?:\\?.*)?$");
    private static final String TEST_USERNAME = "stockwise_auth_test_user";
    private static final String ISSUER = "https://api.stockwise.example";
    private static final String AUDIENCE = "stockwise-web";

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

    @Autowired
    private RefreshTokenFactory refreshTokenFactory;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    void removeTestUsersAndSessionFamilies() {
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
    void refreshRotatesTokenAndNeverReturnsRawRefreshTokenInJson() throws Exception {
        Login login = loginNewUser();
        String csrf = csrfFor(login.refreshToken());
        String originalHash = refreshTokenFactory.hash(login.refreshToken());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(login.refreshToken()))
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String replacement = cookieValue(
                result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(replacement).isNotEqualTo(login.refreshToken());
        assertThat(body).doesNotContain(login.refreshToken(), replacement);
        AuthRefreshToken consumed = authRefreshTokenRepository
                .findByTokenHash(originalHash).orElseThrow();
        assertThat(consumed.getConsumedAt()).isNotNull();
        assertThat(authRefreshTokenRepository.findByTokenHash(
                refreshTokenFactory.hash(replacement))).isPresent();
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .contains("no-store");
    }

    @Test
    void csrfIsRequiredAndSessionBoundWithoutConsumingTokenOnFailure() throws Exception {
        Login login = loginNewUser();
        MvcResult bootstrap = mockMvc.perform(get("/api/v1/auth/csrf")
                        .cookie(refreshCookie(login.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andReturn();
        assertThat(bootstrap.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .contains("no-store");
        String csrf = objectMapper.readTree(bootstrap.getResponse().getContentAsString())
                .get("csrfToken").asText();
        String wrongSessionCsrf = UUID.randomUUID() + csrf.substring(csrf.indexOf('.'));

        assertRefreshUnauthorized(login.refreshToken(), null);
        assertRefreshUnauthorized(login.refreshToken(), wrongSessionCsrf);
        assertThat(authRefreshTokenRepository
                .findByTokenHash(refreshTokenFactory.hash(login.refreshToken()))
                .orElseThrow()
                .getConsumedAt()).isNull();
    }

    @Test
    void replayRevokesSessionFamilyAndPreviouslyIssuedAccessJwt() throws Exception {
        Login login = loginNewUser();
        String csrf = csrfFor(login.refreshToken());

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(login.refreshToken()))
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andReturn();
        String replacement = cookieValue(rotated.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        UUID sessionId = UUID.fromString(login.jwt().getClaimAsString("sid"));

        MvcResult replay = assertRefreshUnauthorized(login.refreshToken(), csrf);
        assertThat(replay.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly")
                .contains("Path=/api/v1/auth")
                .contains("SameSite=Lax")
                .contains("Max-Age=0");

        AuthSession session = authSessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(session.getRevocationReason()).isEqualTo("TOKEN_REUSE");
        List<AuthRefreshToken> family = authRefreshTokenRepository.findAllBySession_Id(sessionId);
        assertThat(family).hasSize(2).allSatisfy(token ->
                assertThat(token.getRevokedAt()).isNotNull());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.jwt().getTokenValue()))
                .andExpect(status().isUnauthorized());
        assertThat(authRefreshTokenRepository
                .findByTokenHash(refreshTokenFactory.hash(replacement))
                .orElseThrow()
                .getRevokedAt()).isNotNull();
    }

    @Test
    void concurrentRefreshesSerializeAndReplayRevokesFamily() throws Exception {
        Login login = loginNewUser();
        String csrf = csrfFor(login.refreshToken());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> refreshAtSameTime(
                    start, login.refreshToken(), csrf));
            Future<Integer> second = executor.submit(() -> refreshAtSameTime(
                    start, login.refreshToken(), csrf));
            start.countDown();
            List<Integer> statuses = List.of(first.get(), second.get());

            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
            AuthSession session = authSessionRepository.findById(
                    UUID.fromString(login.jwt().getClaimAsString("sid"))).orElseThrow();
            assertThat(session.getRevokedAt()).isNotNull();
            assertThat(authRefreshTokenRepository.findAllBySession_Id(session.getId()))
                    .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiredSessionAndSubjectMismatchRejectOtherwiseValidJwt() throws Exception {
        Login login = loginNewUser();
        UUID sessionId = UUID.fromString(login.jwt().getClaimAsString("sid"));
        String wrongSubject = encodeJwt(
                UUID.randomUUID().toString(), sessionId, Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(120));

        assertThatThrownBy(() -> jwtDecoder.decode(wrongSubject))
                .isInstanceOf(JwtException.class);
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + wrongSubject))
                .andExpect(status().isUnauthorized());

        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "update AuthSession session set session.createdAt = :createdAt, "
                                + "session.expiresAt = :expiresAt "
                                + "where session.id = :sessionId")
                .setParameter("createdAt", LocalDateTime.now(ZoneOffset.UTC).minusDays(2))
                .setParameter("expiresAt", LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                .setParameter("sessionId", sessionId)
                .executeUpdate());
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.jwt().getTokenValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenCannotBeUsed() throws Exception {
        Login login = loginNewUser();
        String csrf = csrfFor(login.refreshToken());
        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "update AuthRefreshToken token set token.issuedAt = :issuedAt, "
                                + "token.expiresAt = :expiresAt "
                                + "where token.tokenHash = :tokenHash")
                .setParameter("issuedAt", LocalDateTime.now(ZoneOffset.UTC).minusDays(2))
                .setParameter("expiresAt", LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                .setParameter("tokenHash", refreshTokenFactory.hash(login.refreshToken()))
                .executeUpdate());

        assertRefreshUnauthorized(login.refreshToken(), csrf);
    }

    @Test
    void logoutRevokesSessionFamilyAndClearsCookie() throws Exception {
        Login login = loginNewUser();
        String csrf = csrfFor(login.refreshToken());
        UUID sessionId = UUID.fromString(login.jwt().getClaimAsString("sid"));
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(login.refreshToken()))
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andReturn();
        String currentRefreshToken =
                cookieValue(refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie(currentRefreshToken))
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly")
                .contains("Path=/api/v1/auth")
                .contains("SameSite=Lax")
                .contains("Max-Age=0");
        AuthSession session = authSessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(session.getRevocationReason()).isEqualTo("USER_LOGOUT");
        assertThat(authRefreshTokenRepository.findAllBySession_Id(sessionId))
                .hasSize(2)
                .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.jwt().getTokenValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresValidCsrfAndDoesNotRevokeOnFailure() throws Exception {
        Login login = loginNewUser();
        UUID sessionId = UUID.fromString(login.jwt().getClaimAsString("sid"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie(login.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh authentication failed."))
                .andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .contains("no-store");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("HttpOnly")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=0");
        assertThat(authSessionRepository.findById(sessionId).orElseThrow().getRevokedAt())
                .isNull();
    }

    private int refreshAtSameTime(
            CountDownLatch start, String refreshToken, String csrf) throws Exception {
        start.await();
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(refreshToken))
                        .header("X-CSRF-TOKEN", csrf))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private MvcResult assertRefreshUnauthorized(String refreshToken, String csrf)
            throws Exception {
        var request = post("/api/v1/auth/refresh").cookie(refreshCookie(refreshToken));
        if (csrf != null) {
            request.header("X-CSRF-TOKEN", csrf);
        }
        return mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Refresh authentication failed."))
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                        .contains("no-store"))
                .andReturn();
    }

    private String csrfFor(String refreshToken) throws Exception {
        String body = mockMvc.perform(get("/api/v1/auth/csrf")
                        .cookie(refreshCookie(refreshToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("csrfToken").asText();
    }

    private Login loginNewUser() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        testEmails.add(email);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "Session Test User",
                                "email", email,
                                "password", "Correct-Horse-Battery-1"))))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Correct-Horse-Battery-1"))))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
        return new Login(
                jwtDecoder.decode(accessToken),
                cookieValue(login.getResponse().getHeader(HttpHeaders.SET_COOKIE)));
    }

    private String encodeJwt(
            String subject, UUID sessionId, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();
    }

    private Cookie refreshCookie(String value) {
        return new Cookie(AuthCookieService.REFRESH_COOKIE_NAME, value);
    }

    private String cookieValue(String setCookie) {
        return setCookie.substring(
                (AuthCookieService.REFRESH_COOKIE_NAME + "=").length(),
                setCookie.indexOf(';'));
    }

    private String json(Map<String, String> value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Session integration tests require " + name + " to be set.");
        }
        return value;
    }

    private record Login(Jwt jwt, String refreshToken) {
    }
}
