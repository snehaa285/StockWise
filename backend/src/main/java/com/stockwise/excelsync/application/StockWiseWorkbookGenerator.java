package com.stockwise.excelsync.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql")
public class StockWiseWorkbookGenerator {
    private static final List<Worksheet> WORKSHEETS = List.of(
            new Worksheet(
                    "Business information",
                    "BusinessInformation",
                    new String[] {"BusinessId", "BusinessName", "CreatedAt", "UpdatedAt"},
                    "SELECT HEX(id) AS BusinessId, name AS BusinessName, created_at AS CreatedAt, "
                            + "updated_at AS UpdatedAt FROM business WHERE id = ?"),
            new Worksheet(
                    "Products",
                    "Products",
                    new String[] {"ProductId", "Name", "SKU", "Category", "UnitOfMeasure",
                        "Description", "BatchTracked", "ExpiryTracked", "Active"},
                    "SELECT HEX(p.id) AS ProductId, p.name AS Name, p.sku AS SKU, "
                            + "c.name AS Category, p.unit_of_measure AS UnitOfMeasure, "
                            + "p.description AS Description, "
                            + "COALESCE(p.batch_tracking_override, c.batch_tracking_default) "
                            + "AS BatchTracked, "
                            + "COALESCE(p.expiry_tracking_override, c.expiry_tracking_default) "
                            + "AS ExpiryTracked, (p.archived_at IS NULL) AS Active "
                            + "FROM product p JOIN product_category c "
                            + "ON c.business_id = p.business_id AND c.id = p.category_id "
                            + "WHERE p.business_id = ? ORDER BY p.name"),
            new Worksheet(
                    "Categories",
                    "Categories",
                    new String[] {"CategoryId", "Name", "Description", "BatchTrackingDefault",
                        "ExpiryTrackingDefault", "Active"},
                    "SELECT HEX(id) AS CategoryId, name AS Name, description AS Description, "
                            + "batch_tracking_default AS BatchTrackingDefault, "
                            + "expiry_tracking_default AS ExpiryTrackingDefault, "
                            + "(archived_at IS NULL) AS Active FROM product_category "
                            + "WHERE business_id = ? ORDER BY name"),
            new Worksheet(
                    "Product attributes",
                    "ProductAttributes",
                    new String[] {"ProductId", "ProductName", "Category", "AttributeName",
                        "AttributeType", "Required", "Value"},
                    "SELECT HEX(p.id) AS ProductId, p.name AS ProductName, c.name AS Category, "
                            + "d.name AS AttributeName, v.value_type AS AttributeType, "
                            + "d.is_required AS Required, "
                            + "COALESCE(v.text_value, CAST(v.integer_value AS CHAR), "
                            + "CAST(v.decimal_value AS CHAR), CAST(v.boolean_value AS CHAR), "
                            + "DATE_FORMAT(v.date_value, '%Y-%m-%d'), "
                            + "GROUP_CONCAT(o.label ORDER BY o.position SEPARATOR ', ')) AS Value "
                            + "FROM product_attribute_value v "
                            + "JOIN product p ON p.business_id = v.business_id AND p.id = v.product_id "
                            + "JOIN product_category c ON c.business_id = v.business_id "
                            + "AND c.id = v.category_id "
                            + "JOIN category_attribute_definition d ON d.business_id = v.business_id "
                            + "AND d.category_id = v.category_id AND d.id = v.definition_id "
                            + "LEFT JOIN product_attribute_selection s ON s.value_id = v.id "
                            + "LEFT JOIN category_attribute_option o ON o.id = s.option_id "
                            + "WHERE v.business_id = ? GROUP BY v.id ORDER BY p.name, d.name"),
            new Worksheet(
                    "Locations",
                    "Locations",
                    new String[] {"LocationId", "Name", "Description", "Active"},
                    "SELECT HEX(id) AS LocationId, name AS Name, description AS Description, "
                            + "(archived_at IS NULL) AS Active FROM inventory_location "
                            + "WHERE business_id = ? ORDER BY name"),
            new Worksheet(
                    "Current stock balances",
                    "CurrentStockBalances",
                    new String[] {"ProductId", "ProductName", "SKU", "LocationId",
                        "LocationName", "BatchId", "BatchCode", "ExpiryDate", "Quantity",
                        "UnitOfMeasure"},
                    "SELECT HEX(p.id) AS ProductId, p.name AS ProductName, p.sku AS SKU, "
                            + "HEX(l.id) AS LocationId, l.name AS LocationName, HEX(b.id) AS BatchId, "
                            + "b.batch_code AS BatchCode, b.expiry_date AS ExpiryDate, "
                            + "sb.quantity AS Quantity, p.unit_of_measure AS UnitOfMeasure "
                            + "FROM inventory_stock_balance sb "
                            + "JOIN product p ON p.business_id = sb.business_id AND p.id = sb.product_id "
                            + "JOIN inventory_location l ON l.business_id = sb.business_id "
                            + "AND l.id = sb.location_id "
                            + "LEFT JOIN inventory_batch b ON b.business_id = sb.business_id "
                            + "AND b.product_id = sb.product_id AND b.id = sb.batch_id "
                            + "WHERE sb.business_id = ? ORDER BY p.name, l.name, b.batch_code"),
            new Worksheet(
                    "Inventory movements",
                    "InventoryMovements",
                    new String[] {"MovementId", "OperationId", "OperationType", "MovementType",
                        "ProductId", "ProductName", "Location", "BatchCode", "ExpiryDate",
                        "QuantityDelta", "QuantityAfter", "SourceLocation", "DestinationLocation",
                        "Reason", "ActorEmail", "OccurredAt"},
                    "SELECT HEX(m.id) AS MovementId, HEX(m.operation_id) AS OperationId, "
                            + "o.operation_type AS OperationType, m.movement_type AS MovementType, "
                            + "HEX(p.id) AS ProductId, p.name AS ProductName, l.name AS Location, "
                            + "b.batch_code AS BatchCode, b.expiry_date AS ExpiryDate, "
                            + "m.quantity_delta AS QuantityDelta, m.quantity_after AS QuantityAfter, "
                            + "src.name AS SourceLocation, dst.name AS DestinationLocation, "
                            + "m.reason AS Reason, u.email_normalized AS ActorEmail, "
                            + "m.created_at AS OccurredAt FROM inventory_movement m "
                            + "JOIN inventory_operation o ON o.business_id = m.business_id "
                            + "AND o.id = m.operation_id "
                            + "JOIN product p ON p.business_id = m.business_id AND p.id = m.product_id "
                            + "JOIN inventory_location l ON l.business_id = m.business_id "
                            + "AND l.id = m.location_id "
                            + "LEFT JOIN inventory_batch b ON b.business_id = m.business_id "
                            + "AND b.product_id = m.product_id AND b.id = m.batch_id "
                            + "LEFT JOIN inventory_location src ON src.business_id = m.business_id "
                            + "AND src.id = m.source_location_id "
                            + "LEFT JOIN inventory_location dst ON dst.business_id = m.business_id "
                            + "AND dst.id = m.destination_location_id "
                            + "JOIN app_user u ON u.id = m.actor_user_id "
                            + "WHERE m.business_id = ? ORDER BY m.created_at, m.id"),
            new Worksheet(
                    "Stock receipts",
                    "StockReceipts",
                    new String[] {"OperationId", "OccurredAt", "ProductId", "ProductName",
                        "Location", "BatchCode", "ExpiryDate", "Quantity", "UnitOfMeasure",
                        "ActorEmail"},
                    "SELECT HEX(o.id) AS OperationId, o.created_at AS OccurredAt, "
                            + "HEX(p.id) AS ProductId, p.name AS ProductName, l.name AS Location, "
                            + "b.batch_code AS BatchCode, b.expiry_date AS ExpiryDate, "
                            + "m.quantity_delta AS Quantity, p.unit_of_measure AS UnitOfMeasure, "
                            + "u.email_normalized AS ActorEmail FROM inventory_operation o "
                            + "JOIN inventory_movement m ON m.business_id = o.business_id "
                            + "AND m.operation_id = o.id AND m.movement_type = 'RECEIPT' "
                            + "JOIN product p ON p.business_id = m.business_id AND p.id = m.product_id "
                            + "JOIN inventory_location l ON l.business_id = m.business_id "
                            + "AND l.id = m.location_id "
                            + "LEFT JOIN inventory_batch b ON b.business_id = m.business_id "
                            + "AND b.product_id = m.product_id AND b.id = m.batch_id "
                            + "JOIN app_user u ON u.id = o.created_by_user_id "
                            + "WHERE o.business_id = ? AND o.operation_type = 'RECEIPT' "
                            + "ORDER BY o.created_at, o.id"),
            new Worksheet(
                    "Stock adjustments",
                    "StockAdjustments",
                    new String[] {"OperationId", "OccurredAt", "ProductId", "ProductName",
                        "Location", "BatchCode", "Adjustment", "QuantityDelta", "QuantityAfter",
                        "Reason", "ActorEmail"},
                    "SELECT HEX(o.id) AS OperationId, o.created_at AS OccurredAt, "
                            + "HEX(p.id) AS ProductId, p.name AS ProductName, l.name AS Location, "
                            + "b.batch_code AS BatchCode, m.movement_type AS Adjustment, "
                            + "m.quantity_delta AS QuantityDelta, m.quantity_after AS QuantityAfter, "
                            + "m.reason AS Reason, u.email_normalized AS ActorEmail "
                            + "FROM inventory_operation o JOIN inventory_movement m "
                            + "ON m.business_id = o.business_id AND m.operation_id = o.id "
                            + "JOIN product p ON p.business_id = m.business_id AND p.id = m.product_id "
                            + "JOIN inventory_location l ON l.business_id = m.business_id "
                            + "AND l.id = m.location_id "
                            + "LEFT JOIN inventory_batch b ON b.business_id = m.business_id "
                            + "AND b.product_id = m.product_id AND b.id = m.batch_id "
                            + "JOIN app_user u ON u.id = o.created_by_user_id "
                            + "WHERE o.business_id = ? AND o.operation_type = 'ADJUSTMENT' "
                            + "ORDER BY o.created_at, o.id"),
            new Worksheet(
                    "Stock transfers",
                    "StockTransfers",
                    new String[] {"OperationId", "OccurredAt", "ProductId", "ProductName",
                        "BatchCode", "SourceLocation", "DestinationLocation", "Quantity",
                        "Reason", "ActorEmail"},
                    "SELECT HEX(o.id) AS OperationId, o.created_at AS OccurredAt, "
                            + "HEX(p.id) AS ProductId, p.name AS ProductName, b.batch_code AS BatchCode, "
                            + "src.name AS SourceLocation, dst.name AS DestinationLocation, "
                            + "ABS(outbound.quantity_delta) AS Quantity, outbound.reason AS Reason, "
                            + "u.email_normalized AS ActorEmail FROM inventory_operation o "
                            + "JOIN inventory_movement outbound ON outbound.business_id = o.business_id "
                            + "AND outbound.operation_id = o.id AND outbound.movement_type = 'TRANSFER_OUT' "
                            + "JOIN product p ON p.business_id = outbound.business_id "
                            + "AND p.id = outbound.product_id "
                            + "LEFT JOIN inventory_batch b ON b.business_id = outbound.business_id "
                            + "AND b.product_id = outbound.product_id AND b.id = outbound.batch_id "
                            + "JOIN inventory_location src ON src.business_id = outbound.business_id "
                            + "AND src.id = outbound.source_location_id "
                            + "JOIN inventory_location dst ON dst.business_id = outbound.business_id "
                            + "AND dst.id = outbound.destination_location_id "
                            + "JOIN app_user u ON u.id = o.created_by_user_id "
                            + "WHERE o.business_id = ? AND o.operation_type = 'TRANSFER' "
                            + "ORDER BY o.created_at, o.id"),
            new Worksheet(
                    "Reorder levels",
                    "ReorderLevels",
                    new String[] {"ProductId", "ProductName", "SKU", "LocationId",
                        "LocationName", "ReorderLevel", "QuantityOnHand", "UnitOfMeasure"},
                    "SELECT HEX(p.id) AS ProductId, p.name AS ProductName, p.sku AS SKU, "
                            + "HEX(l.id) AS LocationId, l.name AS LocationName, "
                            + "r.reorder_level AS ReorderLevel, "
                            + "COALESCE(sb.quantity, 0) AS QuantityOnHand, "
                            + "p.unit_of_measure AS UnitOfMeasure FROM inventory_reorder_level r "
                            + "JOIN product p ON p.business_id = r.business_id AND p.id = r.product_id "
                            + "JOIN inventory_location l ON l.business_id = r.business_id "
                            + "AND l.id = r.location_id "
                            + "LEFT JOIN inventory_stock_balance sb ON sb.business_id = r.business_id "
                            + "AND sb.product_id = r.product_id AND sb.location_id = r.location_id "
                            + "WHERE r.business_id = ? ORDER BY p.name, l.name"),
            new Worksheet(
                    "Low-stock alerts",
                    "LowStockAlerts",
                    new String[] {"ProductId", "ProductName", "SKU", "LocationId",
                        "LocationName", "AvailableQuantity", "ReorderLevel", "Shortfall",
                        "UnitOfMeasure"},
                    "SELECT HEX(p.id) AS ProductId, p.name AS ProductName, p.sku AS SKU, "
                            + "HEX(l.id) AS LocationId, l.name AS LocationName, "
                            + "COALESCE(sb.quantity, 0) AS AvailableQuantity, "
                            + "r.reorder_level AS ReorderLevel, "
                            + "r.reorder_level - COALESCE(sb.quantity, 0) AS Shortfall, "
                            + "p.unit_of_measure AS UnitOfMeasure FROM inventory_reorder_level r "
                            + "JOIN product p ON p.business_id = r.business_id AND p.id = r.product_id "
                            + "JOIN inventory_location l ON l.business_id = r.business_id "
                            + "AND l.id = r.location_id "
                            + "LEFT JOIN inventory_stock_balance sb ON sb.business_id = r.business_id "
                            + "AND sb.product_id = r.product_id AND sb.location_id = r.location_id "
                            + "WHERE r.business_id = ? AND COALESCE(sb.quantity, 0) <= r.reorder_level "
                            + "ORDER BY p.name, l.name"),
            new Worksheet(
                    "Expiry alerts",
                    "ExpiryAlerts",
                    new String[] {"ProductId", "ProductName", "SKU", "LocationId",
                        "LocationName", "BatchId", "BatchCode", "ExpiryDate", "DaysUntilExpiry",
                        "AlertStatus", "Quantity", "UnitOfMeasure"},
                    "SELECT HEX(p.id) AS ProductId, p.name AS ProductName, p.sku AS SKU, "
                            + "HEX(l.id) AS LocationId, l.name AS LocationName, HEX(b.id) AS BatchId, "
                            + "b.batch_code AS BatchCode, b.expiry_date AS ExpiryDate, "
                            + "DATEDIFF(b.expiry_date, CURDATE()) AS DaysUntilExpiry, "
                            + "CASE WHEN b.expiry_date < CURDATE() THEN 'EXPIRED' ELSE 'UPCOMING' END "
                            + "AS AlertStatus, sb.quantity AS Quantity, "
                            + "p.unit_of_measure AS UnitOfMeasure FROM inventory_stock_balance sb "
                            + "JOIN product p ON p.business_id = sb.business_id AND p.id = sb.product_id "
                            + "JOIN inventory_location l ON l.business_id = sb.business_id "
                            + "AND l.id = sb.location_id "
                            + "JOIN inventory_batch b ON b.business_id = sb.business_id "
                            + "AND b.product_id = sb.product_id AND b.id = sb.batch_id "
                            + "LEFT JOIN business_inventory_settings settings "
                            + "ON settings.business_id = sb.business_id "
                            + "WHERE sb.business_id = ? AND sb.quantity > 0 "
                            + "AND b.expiry_date <= DATE_ADD(CURDATE(), "
                            + "INTERVAL COALESCE(settings.near_expiry_days, 30) DAY) "
                            + "ORDER BY b.expiry_date, p.name, l.name"));

    private final JdbcTemplate jdbcTemplate;

    public StockWiseWorkbookGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public byte[] generate(UUID businessId) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle dateStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
            CellStyle dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm:ss"));
            CellStyle quantityStyle = workbook.createCellStyle();
            quantityStyle.setDataFormat(dataFormat.getFormat("0.000"));

            for (Worksheet worksheet : WORKSHEETS) {
                writeWorksheet(workbook, worksheet, businessId, dateStyle, dateTimeStyle, quantityStyle);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new MicrosoftIntegrationException(
                    "The StockWise Excel workbook could not be generated.", true, exception);
        }
    }

    private void writeWorksheet(
            XSSFWorkbook workbook,
            Worksheet definition,
            UUID businessId,
            CellStyle dateStyle,
            CellStyle dateTimeStyle,
            CellStyle quantityStyle) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                definition.query(), uuidBytes(businessId));
        XSSFSheet sheet = workbook.createSheet(definition.sheetName());
        Row header = sheet.createRow(0);
        for (int i = 0; i < definition.headers().length; i++) {
            header.createCell(i).setCellValue(definition.headers()[i]);
        }

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Map<String, Object> values = rows.get(rowIndex);
            Row row = sheet.createRow(rowIndex + 1);
            for (int columnIndex = 0; columnIndex < definition.headers().length; columnIndex++) {
                String key = definition.headers()[columnIndex];
                Object value = values.get(key);
                writeCell(
                        row.createCell(columnIndex),
                        key,
                        value,
                        dateStyle,
                        dateTimeStyle,
                        quantityStyle);
            }
        }

        AreaReference area = new AreaReference(
                new CellReference(0, 0),
                new CellReference(rows.size(), definition.headers().length - 1),
                SpreadsheetVersion.EXCEL2007);
        XSSFTable table = sheet.createTable(area);
        table.setName(definition.tableName());
        table.setDisplayName(definition.tableName());
        table.updateHeaders();
        sheet.createFreezePane(0, 1);
    }

    private static void writeCell(
            Cell cell,
            String columnName,
            Object value,
            CellStyle dateStyle,
            CellStyle dateTimeStyle,
            CellStyle quantityStyle) {
        if (value == null) {
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            if (isQuantityColumn(columnName)) {
                cell.setCellStyle(quantityStyle);
            }
        } else if (value instanceof java.sql.Date date) {
            cell.setCellValue(Date.from(date.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof java.sql.Timestamp timestamp) {
            cell.setCellValue(Date.from(timestamp.toInstant()));
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(Date.from(date.atStartOfDay(ZoneOffset.UTC).toInstant()));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(Date.from(dateTime.toInstant(ZoneOffset.UTC)));
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer input = ByteBuffer.wrap(bytes);
            cell.setCellValue(new UUID(input.getLong(), input.getLong()).toString());
        } else if (value instanceof Instant instant) {
            cell.setCellValue(Date.from(instant));
            cell.setCellStyle(dateTimeStyle);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private static boolean isQuantityColumn(String columnName) {
        return switch (columnName) {
            case "Quantity", "QuantityDelta", "QuantityAfter", "QuantityOnHand",
                    "ReorderLevel", "Shortfall", "AvailableQuantity" -> true;
            default -> false;
        };
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    private record Worksheet(
            String sheetName,
            String tableName,
            String[] headers,
            String query) {
    }
}
