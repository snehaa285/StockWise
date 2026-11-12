package com.stockwise.auth.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class AuthMigrationVerificationTest {
    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://[^/:?]+(?::\\d+)?/(?<schema>[^?;/]+)(?:\\?.*)?$");
    private static final String TEST_SCHEMA = "stockwise_auth_test";
    private static final String TEST_USERNAME = "stockwise_auth_test_user";

    @Test
    void appliesV11OnlyToTheVerifiedIsolatedTestSchema() throws Exception {
        String url = requiredEnvironmentVariable("AUTH_TEST_DB_URL");
        String username = requiredEnvironmentVariable("AUTH_TEST_DB_USERNAME");
        String password = requiredEnvironmentVariable("AUTH_TEST_DB_PASSWORD");
        Matcher matcher = MYSQL_URL.matcher(url);
        if (!matcher.matches()
                || !TEST_SCHEMA.equals(matcher.group("schema"))
                || !TEST_USERNAME.equals(username)) {
            throw new IllegalStateException(
                    "Auth migration verification requires the dedicated test datasource.");
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
                        "SELECT COUNT(*) FROM flyway_schema_history "
                                + "WHERE version IN ('1', '2', '3', '4', '5', '6', '7', '8', '9', '10') "
                                + "AND success = 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(10);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("11"))
                .load();
        flyway.migrate();

        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT success FROM flyway_schema_history WHERE version = '11'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean(1)).isTrue();
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Auth migration verification requires " + name + " to be set.");
        }
        return value;
    }
}
