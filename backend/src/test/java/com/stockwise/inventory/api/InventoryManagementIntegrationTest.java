package com.stockwise.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthLoginEventRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import com.stockwise.business.application.InvitationEmailSender;
import com.stockwise.business.persistence.BusinessInvitationRepository;
import com.stockwise.business.persistence.BusinessMembershipRepository;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.inventory.persistence.InventoryMovementRepository;
import com.stockwise.inventory.persistence.InventoryStockBalanceRepository;
import java.nio.ByteBuffer;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.mockito.ArgumentMatchers.anyString;
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
class InventoryManagementIntegrationTest {
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://[^/:?]+(?::\\d+)?/(?<schema>[^?;/]+)(?:\\?.*)?$");
    private static final String TEST_USERNAME = "stockwise_auth_test_user";
    private static final String PASSWORD = "Correct-Horse-Battery-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    @Autowired
    private AuthLoginEventRepository authLoginEventRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessMembershipRepository membershipRepository;

    @Autowired
    private BusinessInvitationRepository invitationRepository;

    @MockBean
    private InvitationEmailSender invitationEmailSender;

    @Autowired
    private InventoryStockBalanceRepository balanceRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    private final List<String> testEmails = new ArrayList<>();
    private final List<UUID> testBusinessIds = new ArrayList<>();

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
                    "Inventory integration tests require the dedicated stockwise_auth_test datasource.");
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
    void cleanUpInventoryAndTestBusinesses() {
        for (UUID businessId : testBusinessIds) {
            byte[] binaryId = uuidBytes(businessId);
            jdbcTemplate.update(
                    "DELETE FROM inventory_reorder_level WHERE business_id = ?", binaryId);
            jdbcTemplate.update(
                    "DELETE FROM business_inventory_settings WHERE business_id = ?", binaryId);
            jdbcTemplate.update("DELETE FROM inventory_movement WHERE business_id = ?", binaryId);
            jdbcTemplate.update(
                    "DELETE FROM inventory_stock_balance WHERE business_id = ?", binaryId);
            jdbcTemplate.update("DELETE FROM inventory_batch WHERE business_id = ?", binaryId);
            jdbcTemplate.update("DELETE FROM inventory_operation WHERE business_id = ?", binaryId);
            jdbcTemplate.update("DELETE FROM inventory_location WHERE business_id = ?", binaryId);
            jdbcTemplate.update("DELETE FROM product WHERE business_id = ?", binaryId);
            jdbcTemplate.update("DELETE FROM product_category WHERE business_id = ?", binaryId);
            invitationRepository.deleteAllByBusinessId(businessId);
            membershipRepository.deleteAllByBusinessId(businessId);
            businessRepository.deleteById(businessId);
        }
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
    void batchReceiptsAdjustmentsTransfersAndIdempotencyAreAtomicAndBusinessScoped()
            throws Exception {
        String owner = registerAndLogin();
        String member = registerAndLogin();
        String outsider = registerAndLogin();
        UUID businessId = createBusiness(owner, "Inventory Tenant");
        UUID secondBusinessId = createBusiness(owner, "Separate Inventory Tenant");
        addMember(owner, businessId, userIdForToken(member));
        UUID categoryId = createCategory(owner, businessId, "Tracked", true, true);
        UUID productId = createProduct(owner, businessId, categoryId, "Olive Oil", null, null);
        UUID sourceId = createLocation(owner, businessId, "Main Store");
        UUID destinationId = createLocation(owner, businessId, "Back Room");
        UUID foreignLocationId = createLocation(owner, secondBusinessId, "Foreign Store");
        mockMvc.perform(get("/api/v1/businesses/{businessId}/locations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/businesses/{businessId}/locations/{locationId}",
                                businessId, sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Main Shop",
                                "description", "Primary receiving point"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main Shop"));

        String receiptJson = json(Map.of(
                "productId", productId,
                "locationId", sourceId,
                "quantity", "10",
                "batchCode", "LOT-1",
                "expiryDate", "2027-01-31"));
        MvcResult receipt = mockMvc.perform(post(
                                "/api/v1/businesses/{businessId}/inventory/receipts", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .header("Idempotency-Key", "receipt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movements[0].quantityAfter").value(10))
                .andReturn();
        String operationId = objectMapper.readTree(receipt.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/receipts", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .header("Idempotency-Key", "receipt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "productId", productId,
                                "locationId", sourceId,
                                "quantity", "10.000",
                                "batchCode", " lot-1 ",
                                "expiryDate", "2027-01-31"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(operationId));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/inventory/batches", businessId)
                        .param("productId", productId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].batchCode").value("LOT-1"))
                .andExpect(jsonPath("$[0].expiryDate").value("2027-01-31"));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/receipts", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .header("Idempotency-Key", "receipt-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "productId", productId,
                                "locationId", sourceId,
                                "quantity", "11",
                                "batchCode", "LOT-1",
                                "expiryDate", "2027-01-31"))))
                .andExpect(status().isConflict());

        String transferBody = json(Map.of(
                "productId", productId,
                "sourceLocationId", sourceId,
                "destinationLocationId", destinationId,
                "quantity", "3",
                "batchCode", "LOT-1",
                "reason", "Rebalance"));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/transfers", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .header("Idempotency-Key", "transfer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movements.length()").value(2))
                .andExpect(jsonPath("$.movements[0].quantityDelta").value(-3))
                .andExpect(jsonPath("$.movements[1].quantityDelta").value(3));

        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/adjustments", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .header("Idempotency-Key", "adjust-too-much")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "productId", productId,
                                "locationId", destinationId,
                                "direction", "DECREASE",
                                "quantity", "4",
                                "batchCode", "LOT-1",
                                "reason", "Count correction"))))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/businesses/{businessId}/inventory/stock", businessId)
                        .param("locationId", sourceId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(7));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/inventory/stock", businessId)
                        .param("locationId", destinationId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(3));

        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/adjustments", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .header("Idempotency-Key", "adjust-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "productId", productId,
                                "locationId", sourceId,
                                "direction", "DECREASE",
                                "quantity", "1",
                                "batchCode", "LOT-1",
                                "reason", "Damaged stock"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movements[0].movementType").value("ADJUSTMENT_OUT"));

        mockMvc.perform(get("/api/v1/businesses/{businessId}/inventory/movements", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/transfers", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "cross-business-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "productId", productId,
                                "sourceLocationId", sourceId,
                                "destinationLocationId", foreignLocationId,
                                "quantity", "1",
                                "batchCode", "LOT-1"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/businesses/{businessId}/inventory/stock", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/locations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Member Location"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/locations/{locationId}",
                                businessId, sourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "categoryId", categoryId,
                                "name", "Olive Oil",
                                "unitOfMeasure", "each",
                                "batchTrackingOverride", false,
                                "expiryTrackingOverride", false,
                                "attributes", Map.of()))))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/businesses/{businessId}/categories/{categoryId}",
                                businessId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Tracked",
                                "batchTrackingDefault", false,
                                "expiryTrackingDefault", false))))
                .andExpect(status().isConflict());
    }

    @Test
    void nonBatchBucketsValidatePrecisionAndTransfersCannotOverdrawStock() throws Exception {
        String owner = registerAndLogin();
        UUID businessId = createBusiness(owner, "Non Batch Inventory");
        UUID categoryId = createCategory(owner, businessId, "Untracked", false, false);
        UUID productId = createProduct(owner, businessId, categoryId, "Rice", null, null);
        UUID sourceId = createLocation(owner, businessId, "Bulk");
        UUID destinationId = createLocation(owner, businessId, "Retail");

        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/receipts", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "fractional-receipt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "productId", productId,
                                "locationId", sourceId,
                                "quantity", "1.125"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movements[0].quantityAfter").value(1.125));

        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/receipts", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "too-precise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "productId", productId,
                                "locationId", sourceId,
                                "quantity", "1.0001"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/receipts", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "unexpected-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "productId", productId,
                                "locationId", sourceId,
                                "quantity", "1",
                                "batchCode", "NOT-ALLOWED"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/businesses/{businessId}/inventory/transfers", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "non-batch-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "productId", productId,
                                "sourceLocationId", sourceId,
                                "destinationLocationId", destinationId,
                                "quantity", "1.125"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/businesses/{businessId}/inventory/stock", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locationId").value(destinationId.toString()))
                .andExpect(jsonPath("$[0].quantity").value(1.125));
    }

    @Test
    void concurrentReplayWithSameKeyAppliesTheReceiptOnlyOnce() throws Exception {
        String owner = registerAndLogin();
        UUID businessId = createBusiness(owner, "Concurrent Inventory");
        UUID categoryId = createCategory(owner, businessId, "Simple", false, false);
        UUID productId = createProduct(owner, businessId, categoryId, "Flour", null, null);
        UUID locationId = createLocation(owner, businessId, "Warehouse");
        String body = json(Map.of(
                "productId", productId,
                "locationId", locationId,
                "quantity", "5"));

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> performConcurrentReceipt(
                    start, owner, businessId, "same-key", body));
            var second = executor.submit(() -> performConcurrentReceipt(
                    start, owner, businessId, "same-key", body));
            start.countDown();
            MvcResult firstResult = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
            MvcResult secondResult = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(201);
            String firstOperation = objectMapper.readTree(
                    firstResult.getResponse().getContentAsString()).get("id").asText();
            String secondOperation = objectMapper.readTree(
                    secondResult.getResponse().getContentAsString()).get("id").asText();
            assertThat(secondOperation).isEqualTo(firstOperation);
            assertThat(movementRepository.findHistory(businessId, productId, locationId,
                    org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements())
                    .isEqualTo(1);
            assertThat(balanceRepository.findPositiveBalances(businessId, productId, locationId))
                    .singleElement()
                    .extracting(balance -> balance.getQuantity())
                    .isEqualTo(new java.math.BigDecimal("5.000"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void lowStockAndReplenishmentAreComputedPerLocationWithAuthorizedConfiguration()
            throws Exception {
        String owner = registerAndLogin();
        String member = registerAndLogin();
        String outsider = registerAndLogin();
        UUID businessId = createBusiness(owner, "Reorder Tenant");
        createBusiness(outsider, "Unrelated Tenant");
        addMember(owner, businessId, userIdForToken(member));
        UUID categoryId = createCategory(owner, businessId, "General", false, false);
        UUID productId = createProduct(owner, businessId, categoryId, "Beans", null, null);
        UUID weightedProductId = createProductWithUnit(
                owner, businessId, categoryId, "Lentils", "kg", null, null);
        UUID firstLocation = createLocation(owner, businessId, "Main");
        UUID secondLocation = createLocation(owner, businessId, "Overflow");

        mockMvc.perform(put(
                            "/api/v1/businesses/{businessId}/inventory/reorder-levels", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "productId", productId,
                                    "locationId", firstLocation,
                                    "reorderLevel", "10"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reorderLevel").value(10));
        mockMvc.perform(put(
                            "/api/v1/businesses/{businessId}/inventory/reorder-levels", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "productId", productId,
                                    "locationId", firstLocation,
                                    "reorderLevel", "12"))))
                    .andExpect(status().isForbidden());

        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/alerts/low-stock", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].availableQuantity").value(0))
                    .andExpect(jsonPath("$[0].shortfallToReorderLevel").value(10))
                    .andExpect(jsonPath("$[0].locationId").value(firstLocation.toString()));

        receiveStock(owner, businessId, productId, firstLocation, "10", null, null);
        receiveStock(owner, businessId, productId, secondLocation, "100", null, null);
        receiveStock(owner, businessId, weightedProductId, secondLocation, "2", null, null);
        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/alerts/low-stock", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].availableQuantity").value(10));

        receiveStock(owner, businessId, productId, firstLocation, "1", null, null);
        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/alerts/low-stock", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post(
                            "/api/v1/businesses/{businessId}/inventory/adjustments", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "productId", productId,
                                    "locationId", firstLocation,
                                    "direction", "DECREASE",
                                    "quantity", "2",
                                    "reason", "Cycle count"))))
                    .andExpect(status().isCreated());

        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/replenishment/suggestions",
                            businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].shortfallToReorderLevel").value(1));
        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/alerts/low-stock", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                    .andExpect(status().isNotFound());

        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/analytics/summary", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.onHandByUnitOfMeasure.each").value(109))
                    .andExpect(jsonPath("$.onHandByUnitOfMeasure.kg").value(2))
                    .andExpect(jsonPath("$.stockedProductCount").value(2))
                    .andExpect(jsonPath("$.activeLocationCount").value(2))
                    .andExpect(jsonPath("$.lowStockLocationProductCount").value(1));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/analytics/movements",
                            businessId)
                            .param("fromDate", today.toString())
                            .param("toDate", today.toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.points.length()").value(3))
                    .andExpect(jsonPath("$.points[0].movementType").value("ADJUSTMENT_OUT"))
                    .andExpect(jsonPath("$.points[0].unitOfMeasure").value("each"))
                    .andExpect(jsonPath("$.points[0].quantityDelta").value(-2))
                    .andExpect(jsonPath("$.points[1].movementType").value("RECEIPT"))
                    .andExpect(jsonPath("$.points[1].unitOfMeasure").value("each"))
                    .andExpect(jsonPath("$.points[1].movementCount").value(3))
                    .andExpect(jsonPath("$.points[2].unitOfMeasure").value("kg"))
                    .andExpect(jsonPath("$.points[2].quantityDelta").value(2));
        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/reports/inventory", businessId)
                            .param("size", "1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.onHandByUnitOfMeasure.each").value(109))
                    .andExpect(jsonPath("$.onHandByUnitOfMeasure.kg").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.items.length()").value(1));
        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/analytics/movements",
                            businessId)
                            .param("fromDate", today.minusDays(365).toString())
                            .param("toDate", today.toString())
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isBadRequest());
        mockMvc.perform(delete(
                            "/api/v1/businesses/{businessId}/inventory/reorder-levels/{productId}/{locationId}",
                            businessId, productId, firstLocation)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isForbidden());
        mockMvc.perform(delete(
                            "/api/v1/businesses/{businessId}/inventory/reorder-levels/{productId}/{locationId}",
                            businessId, productId, firstLocation)
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                    .andExpect(status().isNoContent());
    }

    @Test
    void expiryMonitoringUsesInclusiveBusinessHorizonAndSeparatesExpiredBatches()
            throws Exception {
        String owner = registerAndLogin();
        String admin = registerAndLogin();
        String member = registerAndLogin();
        UUID businessId = createBusiness(owner, "Expiry Tenant");
        UUID otherBusinessId = createBusiness(owner, "Other Expiry Tenant");
        addMemberWithRole(owner, businessId, userIdForToken(admin), "ADMIN");
        addMember(owner, businessId, userIdForToken(member));
        UUID categoryId = createCategory(owner, businessId, "Perishable", true, true);
        UUID productId = createProduct(owner, businessId, categoryId, "Fresh Produce", null, null);
        UUID locationId = createLocation(owner, businessId, "Cold Store");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/settings", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nearExpiryDays").value(30));
        receiveStock(owner, businessId, productId, locationId, "1",
                    "EXPIRES-TODAY", today.toString());
        receiveStock(owner, businessId, productId, locationId, "1",
                    "EXPIRES-30", today.plusDays(30).toString());
        receiveStock(owner, businessId, productId, locationId, "1",
                    "EXPIRES-31", today.plusDays(31).toString());
        receiveStock(owner, businessId, productId, locationId, "1",
                    "EXPIRED", today.minusDays(1).toString());

        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/alerts/expiry", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nearExpiryDays").value(30))
                    .andExpect(jsonPath("$.upcoming.length()").value(2))
                    .andExpect(jsonPath("$.upcoming[0].daysUntilExpiry").value(0))
                    .andExpect(jsonPath("$.upcoming[1].daysUntilExpiry").value(30))
                    .andExpect(jsonPath("$.expired.length()").value(1))
                    .andExpect(jsonPath("$.expired[0].daysUntilExpiry").value(-1));

        mockMvc.perform(put(
                            "/api/v1/businesses/{businessId}/inventory/settings", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("nearExpiryDays", 45))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nearExpiryDays").value(45));
        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/alerts/expiry", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upcoming.length()").value(3))
                    .andExpect(jsonPath("$.expired.length()").value(1));
        mockMvc.perform(get(
                            "/api/v1/businesses/{businessId}/inventory/settings", otherBusinessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nearExpiryDays").value(30));
        mockMvc.perform(put(
                            "/api/v1/businesses/{businessId}/inventory/settings", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("nearExpiryDays", 60))))
                    .andExpect(status().isForbidden());
        mockMvc.perform(put(
                            "/api/v1/businesses/{businessId}/inventory/settings", businessId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("nearExpiryDays", 0))))
                    .andExpect(status().isBadRequest());
    }

    private MvcResult performConcurrentReceipt(
            CountDownLatch start,
            String token,
            UUID businessId,
            String idempotencyKey,
            String body) throws Exception {
        start.await();
        return mockMvc.perform(post(
                        "/api/v1/businesses/{businessId}/inventory/receipts", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andReturn();
    }

    private UUID createBusiness(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID businessId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        testBusinessIds.add(businessId);
        return businessId;
    }

    private void addMember(String owner, UUID businessId, UUID memberId) throws Exception {
        addMemberWithRole(owner, businessId, memberId, "MEMBER");
    }

    private void addMemberWithRole(
            String owner, UUID businessId, UUID memberId, String role) throws Exception {
        reset(invitationEmailSender);
        String memberToken = registerTokenForUser(memberId);
        String email = appUserRepository.findById(memberId).orElseThrow().getEmailNormalized();
        mockMvc.perform(post("/api/v1/businesses/{businessId}/invitations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", role))))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> invitationUrl = ArgumentCaptor.forClass(String.class);
        verify(invitationEmailSender).send(anyString(), anyString(), anyString(), invitationUrl.capture());
        String rawQuery = URI.create(invitationUrl.getValue()).getRawQuery();
        String invitationToken = java.util.Arrays.stream(rawQuery.split("&"))
                .filter(pair -> pair.startsWith("token="))
                .map(pair -> URLDecoder.decode(
                        pair.substring("token=".length()), StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Invitation link did not contain its token."));
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", invitationToken))))
                .andExpect(status().isNoContent());
    }

    private String registerTokenForUser(UUID userId) throws Exception {
        String email = appUserRepository.findById(userId).orElseThrow().getEmailNormalized();
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID createCategory(
            String token, UUID businessId, String name, boolean batch, boolean expiry)
            throws Exception {
        String body = mockMvc.perform(post(
                                "/api/v1/businesses/{businessId}/categories", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "batchTrackingDefault", batch,
                                "expiryTrackingDefault", expiry))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createProduct(
            String token,
            UUID businessId,
            UUID categoryId,
            String name,
            Boolean batchOverride,
            Boolean expiryOverride) throws Exception {
        return createProductWithUnit(
                token, businessId, categoryId, name, "each", batchOverride, expiryOverride);
    }

    private UUID createProductWithUnit(
            String token,
            UUID businessId,
            UUID categoryId,
            String name,
            String unitOfMeasure,
            Boolean batchOverride,
            Boolean expiryOverride) throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("categoryId", categoryId);
        request.put("name", name);
        request.put("unitOfMeasure", unitOfMeasure);
        request.put("batchTrackingOverride", batchOverride);
        request.put("expiryTrackingOverride", expiryOverride);
        request.put("attributes", Map.of());
        String body = mockMvc.perform(post(
                                "/api/v1/businesses/{businessId}/products", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID createLocation(String token, UUID businessId, String name) throws Exception {
        String body = mockMvc.perform(post(
                                "/api/v1/businesses/{businessId}/locations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private void receiveStock(
            String token,
            UUID businessId,
            UUID productId,
            UUID locationId,
            String quantity,
            String batchCode,
            String expiryDate) throws Exception {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("productId", productId);
        request.put("locationId", locationId);
        request.put("quantity", quantity);
        if (batchCode != null) {
            request.put("batchCode", batchCode);
        }
        if (expiryDate != null) {
            request.put("expiryDate", expiryDate);
        }
        mockMvc.perform(post(
                        "/api/v1/businesses/{businessId}/inventory/receipts", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated());
    }

    private String registerAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@inventory.example.test";
        testEmails.add(email);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "Inventory Test User",
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID userIdForToken(String token) {
        String[] parts = token.split("\\.");
        String claims = new String(
                Base64.getUrlDecoder().decode(parts[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        try {
            JsonNode json = objectMapper.readTree(claims);
            return UUID.fromString(json.get("sub").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read the test token subject.", exception);
        }
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Inventory integration tests require " + name + " to be set.");
        }
        return value;
    }
}
