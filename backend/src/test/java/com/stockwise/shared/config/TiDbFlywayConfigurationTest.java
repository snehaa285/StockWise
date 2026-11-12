package com.stockwise.shared.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

class TiDbFlywayConfigurationTest {
    @Test
    void selectsSharedAndTiDbMigrationsOnly() {
        FluentConfiguration configuration = mock(FluentConfiguration.class);

        new TiDbFlywayConfiguration().customize(configuration);

        verify(configuration).locations(
                "classpath:db/migration/common",
                "classpath:db/migration/tidb");
        verifyNoMoreInteractions(configuration);
    }
}
