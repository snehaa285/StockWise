package com.stockwise.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockwise.auth.application.RefreshTokenFactory;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.auth.persistence.AuthRefreshTokenRepository;
import com.stockwise.auth.persistence.AuthLoginEventRepository;
import com.stockwise.auth.persistence.AuthSessionRepository;
import com.stockwise.business.application.InvitationEmailSender;
import com.stockwise.business.persistence.BusinessInvitationRepository;
import com.stockwise.business.persistence.BusinessMembershipRepository;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.catalog.domain.AttributeType;
import com.stockwise.catalog.domain.CategoryAttributeDefinition;
import com.stockwise.catalog.domain.CategoryAttributeOption;
import com.stockwise.catalog.domain.Product;
import com.stockwise.catalog.domain.ProductAttributeValue;
import com.stockwise.catalog.persistence.CategoryAttributeDefinitionRepository;
import com.stockwise.catalog.persistence.CategoryAttributeOptionRepository;
import com.stockwise.catalog.persistence.ProductAttributeSelectionRepository;
import com.stockwise.catalog.persistence.ProductAttributeValueRepository;
import com.stockwise.catalog.persistence.ProductCategoryRepository;
import com.stockwise.catalog.persistence.ProductRepository;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "stockwise.auth.refresh-cookie.secure=false",
        "stockwise.auth.refresh-cookie.same-site=Lax"
})
class CatalogManagementIntegrationTest {
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://[^/:?]+(?::\\d+)?/(?<schema>[^?;/]+)(?:\\?.*)?$");
    private static final String TEST_USERNAME = "stockwise_auth_test_user";

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
    private RefreshTokenFactory refreshTokenFactory;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BusinessMembershipRepository membershipRepository;

    @Autowired
    private BusinessInvitationRepository invitationRepository;

    @MockBean
    private InvitationEmailSender invitationEmailSender;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryAttributeDefinitionRepository definitionRepository;

    @Autowired
    private CategoryAttributeOptionRepository optionRepository;

    @Autowired
    private ProductAttributeValueRepository valueRepository;

    @Autowired
    private ProductAttributeSelectionRepository selectionRepository;

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
                    "Catalog integration tests require the dedicated stockwise_auth_test datasource.");
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
    void cleanUpCatalogAndAuthRecords() {
        testBusinessIds.forEach(businessId -> {
            invitationRepository.deleteAllByBusinessId(businessId);
            for (Product product : productRepository.findAllByBusiness_IdOrderByNameAsc(
                    businessId)) {
                for (ProductAttributeValue value : valueRepository.findAllByProduct_Id(
                        product.getId())) {
                    selectionRepository.deleteAll(
                            selectionRepository.findAllByValue_IdOrderByOption_PositionAsc(
                                    value.getId()));
                    valueRepository.delete(value);
                }
                productRepository.delete(product);
            }
            for (var category : categoryRepository.findAllByBusiness_IdOrderByNameAsc(
                    businessId)) {
                List<CategoryAttributeDefinition> definitions = definitionRepository
                        .findAllByBusiness_IdAndCategory_IdOrderByNameAsc(
                                businessId, category.getId());
                for (CategoryAttributeDefinition definition : definitions) {
                    optionRepository.deleteAllInBatch(
                            optionRepository.findAllByDefinition_IdOrderByPositionAscLabelAsc(
                                    definition.getId()));
                    definitionRepository.delete(definition);
                }
                categoryRepository.delete(category);
            }
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
    void categoriesAndProductsAreTenantScopedAndRoleProtected() throws Exception {
        String owner = registerAndLogin();
        String member = registerAndLogin();
        String outsider = registerAndLogin();
        UUID businessId = createBusiness(owner, "Catalog Business");
        addMember(owner, businessId, userIdForToken(member));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/categories", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(category("Invalid Tracking", false, true))))
                .andExpect(status().isBadRequest());
        SaveCategoryRequest categoryRequest = category("Dry Goods", true, true);

        UUID categoryId = createCategory(owner, businessId, categoryRequest);
        mockMvc.perform(get("/api/v1/businesses/{businessId}/categories", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Dry Goods"));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/categories", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/categories", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(category("Member Category", false, false))))
                .andExpect(status().isForbidden());

        UUID productId = createProduct(owner, businessId, product(categoryId, "Rice", "RICE-1",
                null, null, Map.of()));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchTracked").value(true))
                .andExpect(jsonPath("$.expiryTracked").value(true));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/products", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(categoryId, "Beans", "BEANS-1",
                                null, null, Map.of()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void productTrackingOverridesSkuUniquenessAndArchiveBehaviorAreEnforced()
            throws Exception {
        String owner = registerAndLogin();
        UUID businessId = createBusiness(owner, "Product Rules");
        UUID categoryId = createCategory(owner, businessId, category("Cold Storage", true, true));

        UUID productId = createProduct(owner, businessId, product(
                categoryId, "Milk", "SKU-MILK", false, false, Map.of()));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchTrackingOverride").value(false))
                .andExpect(jsonPath("$.batchTracked").value(false))
                .andExpect(jsonPath("$.expiryTracked").value(false));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/products", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(categoryId, "Invalid Tracking", null,
                                false, null, Map.of()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/products", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(categoryId, "Other Milk", " sku-milk ", null,
                                null, Map.of()))))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/categories/{categoryId}",
                                businessId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(
                                "/api/v1/businesses/{businessId}/categories/{categoryId}/archive",
                                businessId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/businesses/{businessId}/categories/{categoryId}",
                                businessId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/categories/{categoryId}",
                                businessId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "This category is used by products and cannot be deleted. Archive it instead."));
        mockMvc.perform(get("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unusedCategoryCanBeDeletedIncludingItsUnusedAttributeDefinitions()
            throws Exception {
        String owner = registerAndLogin();
        UUID businessId = createBusiness(owner, "Category Deletion");
        UUID categoryId = createCategory(
                owner, businessId, category("Unused", false, false));
        createDefinition(owner, businessId, categoryId, definition(
                "Color", AttributeType.TEXT, false, null,
                constraints(null, null, null, null, null, null, null, null, null, null),
                List.of()));

        mockMvc.perform(delete("/api/v1/businesses/{businessId}/categories/{categoryId}",
                                businessId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/businesses/{businessId}/categories/{categoryId}",
                                businessId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        assertThat(categoryRepository.findById(categoryId)).isEmpty();
    }

    @Test
    void typedAttributeValuesAreValidatedAndDropdownValuesRemainCategoryScoped()
            throws Exception {
        String owner = registerAndLogin();
        UUID businessId = createBusiness(owner, "Typed Attributes");
        UUID categoryId = createCategory(owner, businessId, category("Attributes", false, false));

        UUID textId = createDefinition(owner, businessId, categoryId, definition(
                "Description", AttributeType.TEXT, true, null,
                constraints(2, 8, null, null, null, null, null, null, null, null), List.of()));
        UUID integerId = createDefinition(owner, businessId, categoryId, definition(
                "Count", AttributeType.INTEGER, false, null,
                constraints(null, null, BigInteger.ZERO, BigInteger.valueOf(50),
                        null, null, null, null, null, null), List.of()));
        UUID decimalId = createDefinition(owner, businessId, categoryId, definition(
                "Ratio", AttributeType.DECIMAL, false, null,
                constraints(null, null, null, null, BigDecimal.ZERO,
                        new BigDecimal("1000.000000"), null, null, null, null), List.of()));
        UUID highPrecisionId = createDefinition(owner, businessId, categoryId, definition(
                "Large Decimal", AttributeType.DECIMAL, false, null,
                constraints(null, null, null, null, null, null, null, null, null, null),
                List.of()));
        UUID booleanId = createDefinition(owner, businessId, categoryId, definition(
                "Enabled", AttributeType.BOOLEAN, false, null,
                constraints(null, null, null, null, null, null, null, null, null, null),
                List.of()));
        UUID dateId = createDefinition(owner, businessId, categoryId, definition(
                "Best Before", AttributeType.DATE, false, null,
                constraints(null, null, null, null, null, null,
                        LocalDate.parse("2026-01-01"), LocalDate.parse("2027-12-31"),
                        null, null), List.of()));
        UUID singleId = createDefinition(owner, businessId, categoryId, definition(
                "Flavor", AttributeType.SINGLE_SELECT, false, null,
                constraints(null, null, null, null, null, null, null, null, null, null),
                List.of(option("Plain", true, 0), option("Spiced", true, 1))));
        UUID multiId = createDefinition(owner, businessId, categoryId, definition(
                "Certifications", AttributeType.MULTI_SELECT, false, null,
                constraints(null, null, null, null, null, null, null, null, 1, 2),
                List.of(option("Organic", true, 0), option("Fair Trade", true, 1))));
        List<UUID> singleOptions = optionIds(owner, businessId, categoryId, singleId);
        List<UUID> multiOptions = optionIds(owner, businessId, categoryId, multiId);

        Map<UUID, Object> values = new LinkedHashMap<>();
        values.put(textId, "  cereal  ");
        values.put(integerId, 42);
        values.put(decimalId, new BigDecimal("12.345600"));
        values.put(highPrecisionId, new BigDecimal("123456789012345678"));
        values.put(booleanId, false);
        values.put(dateId, "2026-04-30");
        values.put(singleId, singleOptions.get(0));
        values.put(multiId, List.of(multiOptions.get(0), multiOptions.get(1)));
        UUID productId = createProduct(owner, businessId, product(
                categoryId, "Breakfast Cereal", null, null, null, values));

        mockMvc.perform(get("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes['" + textId + "']").value("cereal"))
                .andExpect(jsonPath("$.attributes['" + integerId + "']").value(42))
                .andExpect(jsonPath("$.attributes['" + highPrecisionId + "']")
                        .value(123456789012345678L))
                .andExpect(jsonPath("$.attributes['" + booleanId + "']").value(false))
                .andExpect(jsonPath("$.attributes['" + singleId + "']").value(
                        singleOptions.get(0).toString()))
                .andExpect(jsonPath("$.attributes['" + multiId + "'].length()").value(2));

        Map<UUID, Object> invalidInteger = new LinkedHashMap<>(values);
        invalidInteger.put(integerId, 4.2);
        mockMvc.perform(post("/api/v1/businesses/{businessId}/products", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(categoryId, "Invalid Integer", null,
                                null, null, invalidInteger))))
                .andExpect(status().isBadRequest());

        Map<UUID, Object> invalidScale = new LinkedHashMap<>(values);
        invalidScale.put(decimalId, new BigDecimal("1.1234567"));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/products", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(categoryId, "Invalid Decimal", null,
                                null, null, invalidScale))))
                .andExpect(status().isBadRequest());

        Map<UUID, Object> duplicateSelections = new LinkedHashMap<>(values);
        duplicateSelections.put(multiId, List.of(multiOptions.get(0), multiOptions.get(0)));
        mockMvc.perform(post("/api/v1/businesses/{businessId}/products", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(categoryId, "Invalid Options", null,
                                null, null, duplicateSelections))))
                .andExpect(status().isBadRequest());

        Map<UUID, Object> missingRequired = new LinkedHashMap<>(values);
        missingRequired.remove(textId);
        mockMvc.perform(post("/api/v1/businesses/{businessId}/products", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(categoryId, "Missing Required", null,
                                null, null, missingRequired))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void attributeRulesFreezeAfterUseAndArchivedValuesArePreserved()
            throws Exception {
        String owner = registerAndLogin();
        UUID businessId = createBusiness(owner, "Frozen Attribute Rules");
        UUID categoryId = createCategory(owner, businessId, category("Frozen", false, false));
        UUID secondCategoryId = createCategory(owner, businessId, category("Other", false, false));
        UUID definitionId = createDefinition(owner, businessId, categoryId, definition(
                "Material", AttributeType.SINGLE_SELECT, true, "Material type",
                constraints(null, null, null, null, null, null, null, null, null, null),
                List.of(option("Steel", true, 0), option("Aluminum", true, 1))));
        UUID optionId = optionIds(owner, businessId, categoryId, definitionId).get(0);
        UUID productId = createProduct(owner, businessId, product(
                categoryId, "Bracket", "BRACKET-1", null, null,
                Map.of(definitionId, optionId)));

        mockMvc.perform(put("/api/v1/businesses/{businessId}/categories/{categoryId}/attributes/{definitionId}",
                                businessId, categoryId, definitionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(definition(
                                "Finish", AttributeType.SINGLE_SELECT, true, "Updated label",
                                constraints(null, null, null, null, null, null, null, null, null, null),
                                List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Finish"));
        mockMvc.perform(put("/api/v1/businesses/{businessId}/categories/{categoryId}/attributes/{definitionId}",
                                businessId, categoryId, definitionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(definition(
                                "Finish", AttributeType.TEXT, true, "Updated label",
                                constraints(null, null, null, null, null, null, null, null, null, null),
                                List.of()))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/businesses/{businessId}/categories/{categoryId}/attributes/{definitionId}/options",
                                businessId, categoryId, definitionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(option("Copper", true, 2))))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/businesses/{businessId}/categories/{categoryId}/attributes/{definitionId}",
                                businessId, categoryId, definitionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes['" + definitionId + "']")
                        .value(optionId.toString()));
        mockMvc.perform(put("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(categoryId, "Bracket Updated", "BRACKET-1",
                                null, null, Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes['" + definitionId + "']")
                        .value(optionId.toString()));
        mockMvc.perform(put("/api/v1/businesses/{businessId}/products/{productId}",
                                businessId, productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(secondCategoryId, "Bracket Updated", "BRACKET-1",
                                null, null, Map.of()))))
                .andExpect(status().isConflict());
    }

    @Test
    void definitionsAndValuesCannotCrossBusinessOrCategoryBoundaries() throws Exception {
        String firstOwner = registerAndLogin();
        String secondOwner = registerAndLogin();
        UUID firstBusiness = createBusiness(firstOwner, "First Tenant");
        UUID secondBusiness = createBusiness(secondOwner, "Second Tenant");
        UUID firstCategory = createCategory(
                firstOwner, firstBusiness, category("First Category", false, false));
        UUID secondCategory = createCategory(
                secondOwner, secondBusiness, category("Second Category", false, false));
        UUID foreignDefinition = createDefinition(
                secondOwner, secondBusiness, secondCategory, definition(
                        "Foreign", AttributeType.TEXT, false, null,
                        constraints(null, 100, null, null, null, null, null, null, null, null),
                        List.of()));

        mockMvc.perform(post("/api/v1/businesses/{businessId}/products", firstBusiness)
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product(firstCategory, "Foreign Attribute", null,
                                null, null, Map.of(foreignDefinition, "value")))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/businesses/{businessId}/categories/{categoryId}/attributes",
                                firstBusiness, secondCategory)
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstOwner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void isolatedDatabaseHasV5AndHibernateValidatesItsSchema() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo("stockwise_auth_test");
        assertThat(jdbcTemplate.queryForObject("SELECT CURRENT_USER()", String.class))
                .startsWith(TEST_USERNAME + "@");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '5'",
                Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('product_category', 'product', 'category_attribute_definition', "
                        + "'category_attribute_option', 'product_attribute_value', "
                        + "'product_attribute_selection')",
                Integer.class))
                .isEqualTo(6);
    }

    private UUID createBusiness(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());
        testBusinessIds.add(id);
        return id;
    }

    private UUID createCategory(
            String token, UUID businessId, SaveCategoryRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/businesses/{businessId}/categories", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());
    }

    private UUID createDefinition(
            String token,
            UUID businessId,
            UUID categoryId,
            CreateAttributeDefinitionRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/businesses/{businessId}/categories/{categoryId}/attributes",
                        businessId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());
    }

    private List<UUID> optionIds(
            String token, UUID businessId, UUID categoryId, UUID definitionId) throws Exception {
        MvcResult result = mockMvc.perform(get(
                        "/api/v1/businesses/{businessId}/categories/{categoryId}/attributes",
                        businessId, categoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode definition : objectMapper.readTree(
                result.getResponse().getContentAsString())) {
            if (definition.get("id").asText().equals(definitionId.toString())) {
                List<UUID> ids = new ArrayList<>();
                definition.get("options").forEach(option ->
                        ids.add(UUID.fromString(option.get("id").asText())));
                return ids;
            }
        }
        throw new AssertionError("Created attribute definition was not returned.");
    }

    private UUID createProduct(
            String token, UUID businessId, SaveProductRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/businesses/{businessId}/products", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("id").asText());
    }

    private void addMember(String owner, UUID businessId, UUID userId) throws Exception {
        reset(invitationEmailSender);
        String memberToken = accessTokensByUser.get(userId);
        if (memberToken == null) {
            throw new IllegalStateException("Test membership requires the member's login token.");
        }
        String email = appUserRepository.findById(userId).orElseThrow().getEmailNormalized();
        mockMvc.perform(post("/api/v1/businesses/{businessId}/invitations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "role", "MEMBER"))))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> invitationUrl = ArgumentCaptor.forClass(String.class);
        verify(invitationEmailSender)
                .send(anyString(), anyString(), anyString(), invitationUrl.capture());
        String query = URI.create(invitationUrl.getValue()).getRawQuery();
        String token = java.util.Arrays.stream(query.split("&"))
                .filter(pair -> pair.startsWith("token="))
                .map(pair -> URLDecoder.decode(
                        pair.substring("token=".length()), StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Invitation link did not contain its token."));
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isNoContent());
    }

    private String registerAndLogin() throws Exception {
        String email = UUID.randomUUID() + "@catalog.example.test";
        testEmails.add(email);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "Catalog Test User",
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

    private SaveCategoryRequest category(
            String name, boolean batchTracking, boolean expiryTracking) {
        return new SaveCategoryRequest(name, null, batchTracking, expiryTracking);
    }

    private CreateAttributeDefinitionRequest definition(
            String name,
            AttributeType type,
            boolean required,
            String description,
            AttributeConstraintsRequest constraints,
            List<AttributeOptionRequest> options) {
        return new CreateAttributeDefinitionRequest(
                name, type, required, description, constraints, options);
    }

    private AttributeConstraintsRequest constraints(
            Integer minTextLength,
            Integer maxTextLength,
            BigInteger minInteger,
            BigInteger maxInteger,
            BigDecimal minDecimal,
            BigDecimal maxDecimal,
            LocalDate minDate,
            LocalDate maxDate,
            Integer minSelections,
            Integer maxSelections) {
        return new AttributeConstraintsRequest(
                minTextLength,
                maxTextLength,
                minInteger,
                maxInteger,
                minDecimal,
                maxDecimal,
                minDate,
                maxDate,
                minSelections,
                maxSelections);
    }

    private AttributeOptionRequest option(String label, boolean active, int position) {
        return new AttributeOptionRequest(label, active, position);
    }

    private SaveProductRequest product(
            UUID categoryId,
            String name,
            String sku,
            Boolean batchOverride,
            Boolean expiryOverride,
            Map<UUID, ?> attributes) {
        Map<UUID, JsonNode> values = new LinkedHashMap<>();
        attributes.forEach((id, value) -> values.put(id, objectMapper.valueToTree(value)));
        return new SaveProductRequest(
                categoryId, name, sku, "unit", null, batchOverride, expiryOverride, values);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Catalog integration tests require " + name + " to be set.");
        }
        return value;
    }
}
