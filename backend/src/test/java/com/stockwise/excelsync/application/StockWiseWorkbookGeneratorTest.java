package com.stockwise.excelsync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class StockWiseWorkbookGeneratorTest {
    @Test
    void createsAllBusinessScopedWorkbookSheetsAndTables() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());
        StockWiseWorkbookGenerator generator = new StockWiseWorkbookGenerator(jdbcTemplate);

        UUID businessId = UUID.randomUUID();
        byte[] content = generator.generate(businessId);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(13);
            assertThat(workbook.getSheet("Business information")).isNotNull();
            assertThat(workbook.getSheet("Products")).isNotNull();
            assertThat(workbook.getSheet("Categories")).isNotNull();
            assertThat(workbook.getSheet("Product attributes")).isNotNull();
            assertThat(workbook.getSheet("Locations")).isNotNull();
            assertThat(workbook.getSheet("Current stock balances")).isNotNull();
            assertThat(workbook.getSheet("Inventory movements")).isNotNull();
            assertThat(workbook.getSheet("Stock receipts")).isNotNull();
            assertThat(workbook.getSheet("Stock adjustments")).isNotNull();
            assertThat(workbook.getSheet("Stock transfers")).isNotNull();
            assertThat(workbook.getSheet("Reorder levels")).isNotNull();
            assertThat(workbook.getSheet("Low-stock alerts")).isNotNull();
            assertThat(workbook.getSheet("Expiry alerts")).isNotNull();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                assertThat(workbook.getSheetAt(i).getTables()).hasSize(1);
                assertThat(workbook.getSheetAt(i).getPaneInformation().isFreezePane()).isTrue();
            }
        }
        ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(13)).queryForList(queries.capture(), parameters.capture());
        byte[] expectedBusinessKey = ByteBuffer.allocate(16)
                .putLong(businessId.getMostSignificantBits())
                .putLong(businessId.getLeastSignificantBits())
                .array();
        assertThat(queries.getAllValues()).allSatisfy(query -> {
            assertThat(query).contains("?");
            assertThat(query.toUpperCase(java.util.Locale.ROOT)).contains(" WHERE ");
        });
        assertThat(parameters.getAllValues()).allSatisfy(arguments -> {
            assertThat(arguments).hasSize(1);
            assertThat((byte[]) arguments[0]).containsExactly(expectedBusinessKey);
        });
    }
}
