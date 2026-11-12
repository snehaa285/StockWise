package com.stockwise.inventory.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class InventoryMigrationVerificationTest {
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://[^/:?]+(?::\\d+)?/(?<schema>[^?;/]+)(?:\\?.*)?$");
    private static final String TEST_SCHEMA = "stockwise_auth_test";
    private static final String TEST_USERNAME = "stockwise_auth_test_user";

    @Test
    void appliesV8OnlyToTheVerifiedIsolatedTestSchema() throws Exception {
        String url = requiredEnvironmentVariable("AUTH_TEST_DB_URL");
        String username = requiredEnvironmentVariable("AUTH_TEST_DB_USERNAME");
        String password = requiredEnvironmentVariable("AUTH_TEST_DB_PASSWORD");
        Matcher matcher = MYSQL_URL.matcher(url);
        if (!matcher.matches()
                || !TEST_SCHEMA.equals(matcher.group("schema"))
                || !TEST_USERNAME.equals(username)) {
            throw new IllegalStateException(
                    "Inventory migration verification requires the dedicated test datasource.");
        }

        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT DATABASE(), CURRENT_USER()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(TEST_SCHEMA);
            assertThat(result.getString(2)).startsWith(TEST_USERNAME + "@");
        }

        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '6'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean(1)).isTrue();
        }

        discardFailedV7Objects(url, username, password);
        discardFailedV8Objects(url, username, password);

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8"))
                .load();
        flyway.repair();
        flyway.migrate();

        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '8'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean(1)).isTrue();
        }
    }

    private static void discardFailedV7Objects(String url, String username, String password)
            throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '7'")) {
            if (result.next() && result.getBoolean(1)) {
                return;
            }
        }
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS inventory_movement");
            statement.execute("DROP TABLE IF EXISTS inventory_stock_balance");
            statement.execute("DROP TABLE IF EXISTS inventory_operation");
            statement.execute("DROP TABLE IF EXISTS inventory_batch");
            statement.execute("DROP TABLE IF EXISTS inventory_location");
            try (var result = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = 'product' "
                            + "AND index_name = 'uk_product_business_id'")) {
                assertThat(result.next()).isTrue();
                if (result.getInt(1) > 0) {
                    statement.execute("ALTER TABLE product DROP INDEX uk_product_business_id");
                }
            }
        }
    }

    private static void discardFailedV8Objects(String url, String username, String password)
            throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '8'")) {
            if (result.next() && result.getBoolean(1)) {
                return;
            }
        }
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS inventory_reorder_level");
            statement.execute("DROP TABLE IF EXISTS business_inventory_settings");
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Inventory migration verification requires " + name + " to be set.");
        }
        return value;
    }
}
