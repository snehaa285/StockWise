package com.stockwise.business.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.auth.application.RefreshTokenFactory;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthLoginEventRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import com.stockwise.business.application.InvitationEmailSender;
import com.stockwise.business.domain.BusinessInvitationStatus;
import com.stockwise.business.domain.Business;
import com.stockwise.business.persistence.BusinessInvitationRepository;
import com.stockwise.business.domain.BusinessMembership;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.business.persistence.BusinessMembershipRepository;
import com.stockwise.business.persistence.BusinessRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "stockwise.auth.refresh-cookie.secure=false",
        "stockwise.auth.refresh-cookie.same-site=Lax"
})
class BusinessManagementIntegrationTest {
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
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessMembershipRepository membershipRepository;

    @Autowired
    private BusinessInvitationRepository invitationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private InvitationEmailSender invitationEmailSender;

    private final List<String> testEmails = new ArrayList<>();
    private final List<UUID> testBusinessIds = new ArrayList<>();
    private final Map<UUID, String> accessTokensByUser = new java.util.HashMap<>();

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
                    "Business integration tests require the dedicated stockwise_auth_test datasource.");
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
    void cleanUpTestBusinessesAndUsers() {
        testBusinessIds.forEach(businessId -> {
            invitationRepository.deleteAllByBusinessId(businessId);
            jdbcTemplate.update("DELETE FROM microsoft_oauth_state WHERE business_id = ?", businessId);
            jdbcTemplate.update("DELETE FROM microsoft_onedrive_connection WHERE business_id = ?", businessId);
            jdbcTemplate.update("DELETE FROM excel_sync_outbox WHERE business_id = ?", businessId);
            membershipRepository.deleteAllByBusinessId(businessId);
            businessRepository.deleteById(businessId);
        });
        testEmails.forEach(email -> appUserRepository.findByEmailNormalized(email)
                .ifPresent(user -> {
                    authLoginEventRepository.deleteAllByUserId(user.getId());
                    membershipRepository.deleteAllByUserId(user.getId());
                    authSessionRepository.findAllByUser_Id(user.getId()).forEach(session ->
                            authRefreshTokenRepository.deleteAllBySessionId(session.getId()));
                    authSessionRepository.deleteAllByUserId(user.getId());
                    appUserRepository.deleteByEmailNormalized(email);
                }));
    }

    @Test
    void invitationRequiresOwnerAndAcceptingTheEmailLinkAddsMemberOnlyOnce() throws Exception {
        reset(invitationEmailSender);
        String ownerToken = registerAndLogin();
        String invitedToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "Invitation Flow");
        var invitedUser = appUserRepository.findById(userIdForToken(invitedToken)).orElseThrow();

        String response = mockMvc.perform(post(
                                "/api/v1/businesses/{businessId}/invitations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", invitedUser.getEmailNormalized(),
                                "role", "ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(invitedUser.getEmailNormalized()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID invitationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        String rawToken = emailedToken();
        String storedHash = invitationRepository.findById(invitationId)
                .orElseThrow().getTokenHash();
        assertThat(storedHash).isNotEqualTo(rawToken);
        assertThat(membershipRepository
                        .findByBusiness_IdAndUser_IdAndRemovedAtIsNull(
                                businessId, invitedUser.getId()))
                .isEmpty();

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invitedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken))))
                .andExpect(status().isNoContent());
        assertThat(membershipRepository
                        .findByBusiness_IdAndUser_IdAndRemovedAtIsNull(
                                businessId, invitedUser.getId())
                        .orElseThrow()
                        .getRole())
                .isEqualTo(BusinessRole.ADMIN);

        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invitedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", rawToken))))
                .andExpect(status().isConflict());
        assertThat(invitationRepository.findById(invitationId).orElseThrow().getStatus())
                .isEqualTo(BusinessInvitationStatus.ACCEPTED);
    }

    @Test
    void invitationResendRotatesTokenAndExpiredInvitationCannotBeAccepted() throws Exception {
        reset(invitationEmailSender);
        String ownerToken = registerAndLogin();
        String invitedToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "Invitation Expiry");
        var invitedUser = appUserRepository.findById(userIdForToken(invitedToken)).orElseThrow();
        String response = invite(ownerToken, businessId, invitedUser.getEmailNormalized());
        UUID invitationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        String staleToken = emailedToken();

        mockMvc.perform(post("/api/v1/businesses/{businessId}/invitations/{id}/resend",
                                businessId, invitationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        String currentToken = emailedToken();
        assertThat(currentToken).isNotEqualTo(staleToken);
        String storedHash = invitationRepository.findById(invitationId)
                .orElseThrow().getTokenHash();
        assertThat(storedHash.equals(sha256(staleToken))).isFalse();
        assertThat(storedHash.equals(sha256(currentToken))).isTrue();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invitedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", staleToken))))
                .andExpect(status().isConflict());

        int updatedRows = jdbcTemplate.update(
                "UPDATE business_invitation SET expires_at = '2000-01-01 00:00:00' "
                        + "WHERE token_hash = ?",
                sha256(currentToken));
        assertThat(updatedRows).isOne();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invitedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", currentToken))))
                .andExpect(status().isConflict());
        assertThat(invitationRepository.findById(invitationId).orElseThrow().getStatus())
                .isEqualTo(BusinessInvitationStatus.EXPIRED);
    }

    @Test
    void invitationCanBeDeclinedOrRevokedAndEmailProviderFailureIsExplicit() throws Exception {
        reset(invitationEmailSender);
        String ownerToken = registerAndLogin();
        String invitedToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "Invitation Decisions");
        var invitedUser = appUserRepository.findById(userIdForToken(invitedToken)).orElseThrow();
        String response = invite(ownerToken, businessId, invitedUser.getEmailNormalized());
        UUID invitationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        String declineToken = emailedToken();
        mockMvc.perform(post("/api/v1/invitations/decline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invitedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", declineToken))))
                .andExpect(status().isNoContent());
        assertThat(invitationRepository.findById(invitationId).orElseThrow().getStatus())
                .isEqualTo(BusinessInvitationStatus.DECLINED);

        String ownerTwo = registerAndLogin();
        UUID secondBusiness = createBusiness(ownerTwo, "Invitation Revocation");
        String secondInvite = invite(ownerTwo, secondBusiness, invitedUser.getEmailNormalized());
        UUID secondId = UUID.fromString(objectMapper.readTree(secondInvite).get("id").asText());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/invitations/{id}/revoke",
                                secondBusiness, secondId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerTwo)))
                .andExpect(status().isNoContent());
        assertThat(invitationRepository.findById(secondId).orElseThrow().getStatus())
                .isEqualTo(BusinessInvitationStatus.REVOKED);

        doThrow(new MailSendException("provider failure"))
                .when(invitationEmailSender)
                .send(anyString(), anyString(), anyString(), anyString());
        String failedEmail = UUID.randomUUID() + "@business.example.test";
        mockMvc.perform(post("/api/v1/businesses/{businessId}/invitations", secondBusiness)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerTwo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", failedEmail, "role", "MEMBER"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Invitation email could not be delivered."));
        assertThat(invitationRepository.findAllByBusiness_IdOrderByCreatedAtDesc(secondBusiness))
                .noneMatch(invitation -> invitation.getEmailNormalized().equals(failedEmail));
    }

    @Test
    void invitationCannotBeCreatedByAdminOrAccessedAcrossBusinesses() throws Exception {
        reset(invitationEmailSender);
        String ownerToken = registerAndLogin();
        String adminToken = registerAndLogin();
        String targetToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "Invitation Permissions");
        addMember(ownerToken, businessId, userIdForToken(adminToken), "ADMIN");
        mockMvc.perform(post("/api/v1/businesses/{businessId}/invitations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", appUserRepository.findById(userIdForToken(targetToken))
                                        .orElseThrow().getEmailNormalized(),
                                "role", "MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessCreatorIsAtomicallyAssignedOwnerAndBusinessListsAreScoped() throws Exception {
        String ownerToken = registerAndLogin();
        String outsiderToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "North Warehouse");

        mockMvc.perform(get("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(businessId.toString()))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
        mockMvc.perform(get("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/businesses/{businessId}", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isNotFound());

        Business business = businessRepository.findById(businessId).orElseThrow();
        var owner = membershipRepository
                .findByBusiness_IdAndUser_IdAndRemovedAtIsNull(
                        businessId, business.getCreatedBy().getId())
                .orElseThrow();
        assertThat(owner.getRole()).isEqualTo(BusinessRole.OWNER);
        assertThat(owner.isActive()).isTrue();
    }

    @Test
    void onlyOwnerCanManageMembersAndRemovalImmediatelyRevokesBusinessAccess()
            throws Exception {
        String ownerToken = registerAndLogin();
        String memberToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "Members Test");
        UUID memberId = userIdForToken(memberToken);

        addMember(ownerToken, businessId, memberId, "MEMBER");
        mockMvc.perform(get("/api/v1/businesses/{businessId}", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/members", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/members/{memberId}",
                                businessId, userIdForToken(ownerToken))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/members/{memberId}",
                                businessId, memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/businesses/{businessId}", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void adminCanUseBusinessButCannotManageMembershipsOrGrantOwnerRole() throws Exception {
        String ownerToken = registerAndLogin();
        String adminToken = registerAndLogin();
        String targetToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "Role Test");
        UUID adminId = userIdForToken(adminToken);
        UUID targetId = userIdForToken(targetToken);

        addMember(ownerToken, businessId, adminId, "ADMIN");
        mockMvc.perform(get("/api/v1/businesses/{businessId}", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/members", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        String targetEmail = appUserRepository.findById(targetId).orElseThrow().getEmailNormalized();
        mockMvc.perform(post("/api/v1/businesses/{businessId}/invitations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", targetEmail, "role", "MEMBER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/businesses/{businessId}/members/{memberId}",
                                businessId, adminId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "OWNER"))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/businesses/{businessId}/members/{memberId}",
                                businessId, targetId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/businesses/{businessId}/members/{memberId}",
                                businessId, userIdForToken(ownerToken))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "MEMBER"))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/businesses/{businessId}", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Unauthorized Rename"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void excelSyncStatusIsBusinessScopedAndConnectionActionsRemainOwnerOnly()
            throws Exception {
        String ownerToken = registerAndLogin();
        String adminToken = registerAndLogin();
        String outsiderToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "OneDrive Permissions");
        addMember(ownerToken, businessId, userIdForToken(adminToken), "ADMIN");

        mockMvc.perform(get("/api/v1/businesses/{businessId}/excel-sync", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()))
                .andExpect(jsonPath("$.status").value("DISCONNECTED"));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/excel-sync", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/excel-sync/connect", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/excel-sync/connect", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void ownerCanChangeMemberRoleAndRemovedMembershipCanBeReaddedWithoutOverwritingHistory()
            throws Exception {
        String ownerToken = registerAndLogin();
        String memberToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "Rejoin Test");
        UUID memberId = userIdForToken(memberToken);
        addMember(ownerToken, businessId, memberId, "MEMBER");

        mockMvc.perform(patch("/api/v1/businesses/{businessId}/members/{memberId}",
                                businessId, memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/members/{memberId}",
                                businessId, memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());
        addMember(ownerToken, businessId, memberId, "MEMBER");

        assertThat(membershipRepository.findAllByBusiness_IdAndRemovedAtIsNullOrderByCreatedAtAsc(
                        businessId))
                .filteredOn(membership -> membership.getUser().getId().equals(memberId))
                .hasSize(1)
                .extracting(BusinessMembership::getRole)
                .containsExactly(BusinessRole.MEMBER);
    }

    @Test
    void directMemberAdditionIsDisabledAndUnauthenticatedBusinessAccessIsRejected()
            throws Exception {
        String ownerToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "Missing User Test");
        mockMvc.perform(get("/api/v1/businesses"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/members", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", UUID.randomUUID(), "role", "MEMBER"))))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void databaseConstraintPreventsMoreThanOneActiveOwner() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        UUID businessId = createBusiness(ownerToken, "Owner Constraint Test");
        Business business = businessRepository.findById(businessId).orElseThrow();
        var otherUser = appUserRepository.findById(userIdForToken(otherToken)).orElseThrow();

        assertThatThrownBy(() -> membershipRepository.saveAndFlush(
                BusinessMembership.create(business, otherUser, BusinessRole.OWNER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migrationAndConnectedDatabaseIdentityAreTheDedicatedTestTarget() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo("stockwise_auth_test");
        assertThat(jdbcTemplate.queryForObject("SELECT CURRENT_USER()", String.class))
                .startsWith(TEST_USERNAME + "@");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '4'",
                Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() "
                        + "AND table_name = 'business_membership' "
                        + "AND index_name = 'uk_business_membership_active_owner' "
                        + "AND non_unique = 0",
                Integer.class))
                .isEqualTo(1);
    }

    private UUID createBusiness(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID businessId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        testBusinessIds.add(businessId);
        return businessId;
    }

    private void addMember(String ownerToken, UUID businessId, UUID memberId, String role)
            throws Exception {
        String memberToken = accessTokensByUser.get(memberId);
        if (memberToken == null) {
            throw new IllegalStateException("Test membership requires the member's login token.");
        }
        String email = appUserRepository.findById(memberId).orElseThrow().getEmailNormalized();
        mockMvc.perform(post("/api/v1/businesses/{businessId}/invitations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", role))))
                .andExpect(status().isCreated());
        String token = emailedToken();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isNoContent());
    }

    private String invite(String ownerToken, UUID businessId, String email) throws Exception {
        return mockMvc.perform(post(
                                "/api/v1/businesses/{businessId}/invitations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", "MEMBER"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String emailedToken() {
        ArgumentCaptor<String> invitationUrl = ArgumentCaptor.forClass(String.class);
        verify(invitationEmailSender, atLeastOnce())
                .send(anyString(), anyString(), anyString(), invitationUrl.capture());
        String query = URI.create(invitationUrl.getValue()).getRawQuery();
        return java.util.Arrays.stream(query.split("&"))
                .filter(pair -> pair.startsWith("token="))
                .map(pair -> URLDecoder.decode(pair.substring("token=".length()), StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Invitation link did not contain its token."));
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the runtime.", exception);
        }
    }

    private String registerAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@business.example.test";
        testEmails.add(email);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "Business Test User",
                                "email", email,
                                "password", "Correct-Horse-Battery-1"))))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email,
                                "password", "Correct-Horse-Battery-1"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();
        accessTokensByUser.put(userIdForToken(token), token);
        return token;
    }

    private UUID userIdForToken(String token) throws Exception {
        String[] parts = token.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        return UUID.fromString(objectMapper.readTree(claims).get("sub").asText());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Map<String, ?> value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Business integration tests require " + name + " to be set.");
        }
        return value;
    }
}
