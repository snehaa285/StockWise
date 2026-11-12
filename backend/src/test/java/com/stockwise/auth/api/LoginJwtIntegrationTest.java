package com.stockwise.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.auth.application.RefreshTokenFactory;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.domain.AuthRefreshToken;
import com.stockwise.auth.domain.AuthSession;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthLoginEventRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;
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
        "stockwise.auth.jwt.access-token-ttl=PT2H",
        "STOCKWISE_JWT_ACCESS_TOKEN_TTL=PT2H",
        "stockwise.auth.refresh-cookie.secure=false",
        "stockwise.auth.refresh-cookie.same-site=Lax"
})
class LoginJwtIntegrationTest {
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
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    private final List<String> testEmails = new CopyOnWriteArrayList<>();

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
                    "Login JWT tests require the dedicated stockwise_auth_test datasource.");
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
    void removeTestUsers() {
        testEmails.forEach(email -> appUserRepository.findByEmailNormalized(email)
                .ifPresent(user -> {
                    authLoginEventRepository.deleteAllByUserId(user.getId());
                    authSessionRepository.findAllByUser_Id(user.getId()).forEach(session ->
                            authRefreshTokenRepository.deleteAllBySessionId(session.getId()));
                    authSessionRepository.deleteAllByUserId(user.getId());
                    appUserRepository.deleteByEmailNormalized(email);
                }));
        appUserRepository.flush();
    }

    @Test
    void loginNormalizesEmailAndIssuesJwtWithRequiredClaims() throws Exception {
        String email = registerUser();
        String loginJson = json(Map.of(
                "email", " " + email.toUpperCase() + " ",
                "password", "Correct-Horse-Battery-1"));

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();
        String firstToken = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(firstToken).get("accessToken").asText();
        String refreshCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(refreshCookie)
                .contains("HttpOnly")
                .contains("Path=/api/v1/auth")
                .contains("SameSite=Lax")
                .contains("Max-Age=")
                .doesNotContain("Secure");
        long cookieMaxAge = Long.parseLong(refreshCookie
                .substring(refreshCookie.indexOf("Max-Age=") + "Max-Age=".length())
                .split(";")[0]);
        assertThat(cookieMaxAge).isPositive().isLessThanOrEqualTo(604800);
        String rawRefreshToken = refreshCookie.substring(
                (AuthCookieService.REFRESH_COOKIE_NAME + "=").length(),
                refreshCookie.indexOf(';'));

        Jwt decoded = jwtDecoder.decode(token);
        assertThat(decoded.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
        assertThat(decoded.getSubject()).isNotBlank();
        assertThat(decoded.getClaimAsString("sid")).isNotBlank();
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(decoded.getId()).isNotBlank();
        UUID sessionId = UUID.fromString(decoded.getClaimAsString("sid"));
        AuthSession session = authSessionRepository.findById(sessionId).orElseThrow();
        assertThat(Duration.between(session.getCreatedAt(), session.getExpiresAt()))
                .isEqualTo(Duration.ofDays(30));
        AuthRefreshToken storedRefreshToken = authRefreshTokenRepository
                .findByTokenHash(refreshTokenFactory.hash(rawRefreshToken))
                .orElseThrow();
        assertThat(storedRefreshToken.getTokenHash())
                .hasSize(64)
                .isNotEqualTo(rawRefreshToken);
        assertThat(Duration.between(
                storedRefreshToken.getIssuedAt(), storedRefreshToken.getExpiresAt()))
                .isEqualTo(Duration.ofDays(7));

        String secondTokenResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Jwt secondDecoded = jwtDecoder.decode(
                objectMapper.readTree(secondTokenResponse).get("accessToken").asText());
        assertThat(secondDecoded.getId()).isNotEqualTo(decoded.getId());
        assertThat(secondDecoded.getClaimAsString("sid"))
                .isNotEqualTo(decoded.getClaimAsString("sid"));

        MvcResult profileResult = mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(decoded.getSubject()))
                .andExpect(jsonPath("$.displayName").value("Login Test User"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        assertThat(profileResult.getResponse().getContentAsString())
                .doesNotContain(token, rawRefreshToken, storedRefreshToken.getTokenHash());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidSignature(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileIdentityComesFromJwtAndCannotBeOverriddenByRequestParameter() throws Exception {
        String currentUserEmail = registerUser();
        String otherUserEmail = registerUser();
        String token = loginAndGetAccessToken(currentUserEmail);
        AppUser currentUser = appUserRepository.findByEmailNormalized(currentUserEmail)
                .orElseThrow();
        AppUser otherUser = appUserRepository.findByEmailNormalized(otherUserEmail)
                .orElseThrow();

        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .param("userId", otherUser.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(currentUser.getId().toString()))
                .andExpect(jsonPath("$.email").value(currentUserEmail))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(otherUser.getEmailNormalized(), otherUser.getId().toString());
    }

    @Test
    void invalidCredentialsHaveOneGenericResponse() throws Exception {
        String email = registerUser();
        String wrongPassword = json(Map.of("email", email, "password", "Wrong-Password-1"));
        String unknownUser = json(Map.of(
                "email", uniqueEmail(),
                "password", "Wrong-Password-1"));

        String knownUserResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPassword))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String unknownUserResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownUser))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(knownUserResponse).get("detail").asText())
                .isEqualTo("Invalid email or password.");
        assertThat(objectMapper.readTree(unknownUserResponse).get("detail").asText())
                .isEqualTo("Invalid email or password.");
        assertThat(knownUserResponse).doesNotContain(email, "Wrong-Password-1");
    }

    @Test
    void loginValidatesEmailAndPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "invalid-email", "password", "nonempty"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "person@example.test", "password", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void jwtDecoderRejectsInvalidSignatureIssuerAudienceAndExpiry() {
        String validToken = encodeJwt(ISSUER, AUDIENCE, Instant.now().minusSeconds(1),
                Instant.now().plusSeconds(120));
        String[] parts = validToken.split("\\.");
        String signature = parts[2];
        parts[2] = (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);
        String invalidSignatureToken = String.join(".", parts);
        assertThatThrownBy(() -> jwtDecoder.decode(invalidSignatureToken))
                .isInstanceOf(JwtException.class);

        String wrongIssuer = encodeJwt(
                "https://wrong-issuer.example", AUDIENCE,
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(120));
        assertThatThrownBy(() -> jwtDecoder.decode(wrongIssuer))
                .isInstanceOf(JwtException.class);

        String wrongAudience = encodeJwt(
                ISSUER, "different-client",
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(120));
        assertThatThrownBy(() -> jwtDecoder.decode(wrongAudience))
                .isInstanceOf(JwtException.class);

        String expired = encodeJwt(
                ISSUER, AUDIENCE,
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        assertThatThrownBy(() -> jwtDecoder.decode(expired))
                .isInstanceOf(JwtException.class);
    }

    private String encodeJwt(String issuer, String audience, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("sid", UUID.randomUUID().toString())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();
    }

    private String registerUser() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "Login Test User",
                                "email", email,
                                "password", "Correct-Horse-Battery-1"))))
                .andExpect(status().isCreated());
        return email;
    }

    private String loginAndGetAccessToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Correct-Horse-Battery-1"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String invalidSignature(String token) {
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        return String.join(".", parts);
    }

    private String uniqueEmail() {
        String email = UUID.randomUUID() + "@example.test";
        testEmails.add(email);
        return email;
    }

    private String json(Map<String, String> value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Login JWT tests require " + name + " to be set.");
        }
        return value;
    }
}
