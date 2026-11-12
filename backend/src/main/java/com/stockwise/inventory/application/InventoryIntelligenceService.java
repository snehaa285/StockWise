package com.stockwise.inventory.application;

import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.business.domain.Business;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.inventory.api.BusinessInventorySettingsResponse;
import com.stockwise.inventory.api.ExpiryMonitoringResponse;
import com.stockwise.inventory.api.ExpiryStockResponse;
import com.stockwise.inventory.api.InventoryAnalyticsSummaryResponse;
import com.stockwise.inventory.api.InventoryMovementTrendPoint;
import com.stockwise.inventory.api.InventoryMovementTrendsResponse;
import com.stockwise.inventory.api.InventoryStockReportResponse;
import com.stockwise.inventory.api.LowStockAlertResponse;
import com.stockwise.inventory.api.UpdateBusinessInventorySettingsRequest;
import com.stockwise.inventory.domain.BusinessInventorySettings;
import com.stockwise.inventory.persistence.BusinessInventorySettingsRepository;
import com.stockwise.inventory.persistence.InventoryBatchRepository;
import com.stockwise.inventory.persistence.InventoryLocationRepository;
import com.stockwise.inventory.persistence.InventoryReorderLevelRepository;
import com.stockwise.inventory.persistence.InventoryStockBalanceRepository;
import com.stockwise.excelsync.application.ExcelSyncQueueService;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class InventoryIntelligenceService {
    public static final int DEFAULT_NEAR_EXPIRY_DAYS = 30;
    public static final int MAX_NEAR_EXPIRY_DAYS = 3650;
    private static final String MOVEMENT_TREND_QUERY = """
            SELECT DATE(movement.created_at) AS movement_date,
                   movement_type,
                   product.unit_of_measure AS unit_of_measure,
                   COUNT(*) AS movement_count,
                   SUM(movement.quantity_delta) AS quantity_delta
            FROM inventory_movement movement
            JOIN product
              ON product.business_id = movement.business_id
             AND product.id = movement.product_id
            WHERE movement.business_id = ?
              AND movement.created_at >= ?
              AND movement.created_at < ?
            GROUP BY DATE(movement.created_at), movement.movement_type,
                     product.unit_of_measure
            ORDER BY movement_date, movement.movement_type, product.unit_of_measure
            """;

    private final BusinessAccessService businessAccessService;
    private final BusinessRepository businessRepository;
    private final BusinessInventorySettingsRepository settingsRepository;
    private final InventoryReorderLevelRepository reorderLevelRepository;
    private final InventoryStockBalanceRepository balanceRepository;
    private final InventoryLocationRepository locationRepository;
    private final InventoryBatchRepository batchRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ExcelSyncQueueService syncQueueService;

    public InventoryIntelligenceService(
            BusinessAccessService businessAccessService,
            BusinessRepository businessRepository,
            BusinessInventorySettingsRepository settingsRepository,
            InventoryReorderLevelRepository reorderLevelRepository,
            InventoryStockBalanceRepository balanceRepository,
            InventoryLocationRepository locationRepository,
            InventoryBatchRepository batchRepository,
            JdbcTemplate jdbcTemplate,
            ExcelSyncQueueService syncQueueService) {
        this.businessAccessService = businessAccessService;
        this.businessRepository = businessRepository;
        this.settingsRepository = settingsRepository;
        this.reorderLevelRepository = reorderLevelRepository;
        this.balanceRepository = balanceRepository;
        this.locationRepository = locationRepository;
        this.batchRepository = batchRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.syncQueueService = syncQueueService;
    }

    @Transactional(readOnly = true)
    public List<LowStockAlertResponse> lowStockAlerts(UUID businessId, UUID actorId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        return reorderLevelRepository.findLowStockForBusiness(businessId).stream()
                .map(LowStockAlertResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpiryMonitoringResponse expiryMonitoring(UUID businessId, UUID actorId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        return expiryMonitoring(businessId, LocalDate.now(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public BusinessInventorySettingsResponse settings(UUID businessId, UUID actorId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        return settingsRepository.findByBusiness_Id(businessId)
                .map(value -> new BusinessInventorySettingsResponse(
                        businessId, value.getNearExpiryDays(), value.getUpdatedAt()))
                .orElseGet(() -> new BusinessInventorySettingsResponse(
                        businessId, DEFAULT_NEAR_EXPIRY_DAYS, null));
    }

    @Transactional
    public BusinessInventorySettingsResponse updateSettings(
            UUID businessId,
            UUID actorId,
            UpdateBusinessInventorySettingsRequest request) {
        businessAccessService.requireRole(
                businessId, actorId, BusinessRole.OWNER, BusinessRole.ADMIN);
        if (request.nearExpiryDays() < 1
                || request.nearExpiryDays() > MAX_NEAR_EXPIRY_DAYS) {
            throw new InventoryValidationException(
                    "nearExpiryDays",
                    "Near-expiry horizon must be between 1 and "
                            + MAX_NEAR_EXPIRY_DAYS + " days.");
        }
        Business business = businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(BusinessNotFoundException::new);
        BusinessInventorySettings settings = settingsRepository.findByBusiness_Id(businessId)
                .orElseGet(() -> BusinessInventorySettings.create(
                        business, request.nearExpiryDays()));
        settings.setNearExpiryDays(request.nearExpiryDays());
        BusinessInventorySettings saved = settingsRepository.saveAndFlush(settings);
        syncQueueService.enqueueChange(businessId, "SETTINGS");
        return new BusinessInventorySettingsResponse(
                businessId, saved.getNearExpiryDays(), saved.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public InventoryAnalyticsSummaryResponse summary(UUID businessId, UUID actorId) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        LocalDate asOfDate = LocalDate.now(ZoneOffset.UTC);
        ExpiryMonitoringResponse expiry = expiryMonitoring(businessId, asOfDate);
        long lowStockCount = reorderLevelRepository
                .findLowStockForBusiness(businessId).size();
        return new InventoryAnalyticsSummaryResponse(
                businessId,
                asOfDate,
                onHandByUnitOfMeasure(businessId),
                balanceRepository.countStockedProductsForBusiness(businessId),
                locationRepository.countByBusiness_IdAndArchivedAtIsNull(businessId),
                lowStockCount,
                expiry.upcoming().size(),
                expiry.expired().size());
    }

    @Transactional(readOnly = true)
    public InventoryMovementTrendsResponse movementTrends(
            UUID businessId, UUID actorId, LocalDate fromDate, LocalDate toDate) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new InventoryValidationException(
                    "fromDate", "A valid inclusive movement date range is required.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= 365
                || toDate.equals(LocalDate.MAX)) {
            throw new InventoryValidationException(
                    "toDate", "Movement trends support ranges of at most 365 days.");
        }
        LocalDateTime start = fromDate.atStartOfDay();
        LocalDateTime endExclusive = toDate.plusDays(1).atStartOfDay();
        List<InventoryMovementTrendPoint> points = jdbcTemplate.query(
                MOVEMENT_TREND_QUERY,
                statement -> {
                    statement.setBytes(1, uuidBytes(businessId));
                    statement.setTimestamp(2, Timestamp.valueOf(start));
                    statement.setTimestamp(3, Timestamp.valueOf(endExclusive));
                },
                (result, row) -> new InventoryMovementTrendPoint(
                        result.getDate("movement_date").toLocalDate(),
                        result.getString("movement_type"),
                        result.getString("unit_of_measure"),
                        result.getLong("movement_count"),
                        result.getBigDecimal("quantity_delta")));
        return new InventoryMovementTrendsResponse(fromDate, toDate, points);
    }

    @Transactional(readOnly = true)
    public InventoryStockReportResponse stockReport(
            UUID businessId, UUID actorId, int page, int size) {
        businessAccessService.requireActiveMembership(businessId, actorId);
        if (page < 0 || size < 1 || size > 200) {
            throw new InventoryValidationException(
                    "page", "Stock report page must be nonnegative and size between 1 and 200.");
        }
        Page<InventoryStockReportItem> results = balanceRepository.findStockReport(
                businessId,
                PageRequest.of(page, size, Sort.unsorted()));
        return new InventoryStockReportResponse(
                businessId,
                LocalDateTime.now(ZoneOffset.UTC),
                onHandByUnitOfMeasure(businessId),
                results.getContent(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages());
    }

    private ExpiryMonitoringResponse expiryMonitoring(UUID businessId, LocalDate asOfDate) {
        int horizonDays = settingsRepository.findByBusiness_Id(businessId)
                .map(BusinessInventorySettings::getNearExpiryDays)
                .map(Short::intValue)
                .orElse(DEFAULT_NEAR_EXPIRY_DAYS);
        LocalDate throughDate = asOfDate.plusDays(horizonDays);
        List<ExpiryStockResponse> upcoming = batchRepository
                .findPositiveStockByExpiryDate(businessId, asOfDate, throughDate).stream()
                .map(stock -> ExpiryStockResponse.from(stock, asOfDate))
                .toList();
        List<ExpiryStockResponse> expired = batchRepository
                .findPositiveStockBeforeDate(businessId, asOfDate).stream()
                .map(stock -> ExpiryStockResponse.from(stock, asOfDate))
                .toList();
        return new ExpiryMonitoringResponse(asOfDate, horizonDays, upcoming, expired);
    }

    private Map<String, BigDecimal> onHandByUnitOfMeasure(UUID businessId) {
        Map<String, BigDecimal> quantities = new LinkedHashMap<>();
        balanceRepository.sumPositiveQuantityByUnitForBusiness(businessId)
                .forEach(total -> quantities.put(total.unitOfMeasure(), total.quantity()));
        return Collections.unmodifiableMap(quantities);
    }

    private byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
