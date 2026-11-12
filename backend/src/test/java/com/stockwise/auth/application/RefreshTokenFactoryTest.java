package com.stockwise.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenFactoryTest {
    private final RefreshTokenFactory factory = new RefreshTokenFactory();

    @Test
    void generatesOpaqueCryptographicallyRandomTokensAndSha256Hashes() {
        RefreshTokenFactory.GeneratedRefreshToken first = factory.generate();
        RefreshTokenFactory.GeneratedRefreshToken second = factory.generate();

        assertThat(first.value()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first.hash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(factory.hash(first.value())).isEqualTo(first.hash());
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(first.hash()).isNotEqualTo(first.value());
    }
}
