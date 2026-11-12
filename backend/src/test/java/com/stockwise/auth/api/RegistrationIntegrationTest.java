package com.stockwise.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.persistence.AppUserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "stockwise.auth.refresh-cookie.secure=false",
        "stockwise.auth.refresh-cookie.same-site=Lax"
})
class RegistrationIntegrationTest {
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://[^/:?]+(?::\\d+)?/(?<schema>[^?;/]+)(?:\\?.*)?$");
    private static final String TEST_USERNAME = "stockwise_auth_test_user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                    "Registration integration tests require the dedicated stockwise_auth_test datasource.");
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
        testEmails.forEach(appUserRepository::deleteByEmailNormalized);
        appUserRepository.flush();
    }

    @Test
    void registersUserWithNormalizedEmailAndBcryptPassword() throws Exception {
        String email = uniqueEmail();
        String password = "Correct-Horse-Battery-1";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Asha Patel", "  " + email.toUpperCase() + "  ", password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Asha Patel"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        AppUser persistedUser = appUserRepository.findByEmailNormalized(email).orElseThrow();
        assertThat(persistedUser.getId()).isNotNull();
        assertThat(persistedUser.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, persistedUser.getPasswordHash())).isTrue();
        assertThat(persistedUser.getCreatedAt()).isNotNull();
        assertThat(persistedUser.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsBlankDisplayNameInvalidEmailAndShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(" ", uniqueEmail(), "Correct-Horse-Battery-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.displayName").exists());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Asha Patel", "not-an-email", "Correct-Horse-Battery-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Asha Patel", uniqueEmail(), "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void rejectsPasswordsLongerThanBcryptUtf8Limit() throws Exception {
        String multibytePassword = "é".repeat(37);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Asha Patel", uniqueEmail(), multibytePassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void rejectsDuplicateEmailRegardlessOfLetterCase() throws Exception {
        String email = uniqueEmail();
        String password = "Correct-Horse-Battery-1";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Asha Patel", email, password)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Another User", email.toUpperCase(), password)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists."));

        assertThat(appUserRepository.findByEmailNormalized(email)).isPresent();
    }

    @Test
    void concurrentDuplicateRegistrationIsRejectedByDatabaseUniqueConstraint() throws Exception {
        String email = uniqueEmail();
        CountDownLatch bothRequestsCheckedEmail = new CountDownLatch(2);
        doAnswer(invocation -> {
            boolean exists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) > 0 FROM app_user WHERE email_normalized = ?",
                    Boolean.class,
                    email));
            bothRequestsCheckedEmail.countDown();
            if (!bothRequestsCheckedEmail.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent registration test did not reach its barrier.");
            }
            return exists;
        }).when(appUserRepository).existsByEmailNormalized(email);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> registerStatus(email, "First User"));
            Future<Integer> second = executor.submit(() -> registerStatus(email, "Second User"));

            assertThat(bothRequestsCheckedEmail.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(appUserRepository.findByEmailNormalized(email)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void responseSerializesOnlyTheSafeUserDtoFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("Asha Patel", uniqueEmail(), "Correct-Horse-Battery-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.displayName").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private String requestJson(String displayName, String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "displayName", displayName,
                "email", email,
                "password", password));
    }

    private String uniqueEmail() {
        String email = UUID.randomUUID() + "@example.test";
        testEmails.add(email);
        return email;
    }

    private int registerStatus(String email, String displayName) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(displayName, email, "Correct-Horse-Battery-1")))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Registration integration tests require " + name + " to be set.");
        }
        return value;
    }
}
