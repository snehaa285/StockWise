package com.stockwise.excelsync.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class MicrosoftTokenCipherTest {
    @Test
    void encryptsTokensWithAuthenticatedEncryptionAndRoundTrips() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        MicrosoftTokenCipher cipher =
                new MicrosoftTokenCipher(Base64.getEncoder().encodeToString(key));
        String token = "test-token-value";

        String encrypted = cipher.encrypt(token);

        assertThat(encrypted).isNotEqualTo(token);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(token);
        assertThat(cipher.isConfigured()).isTrue();
    }

    @Test
    void rejectsKeysThatAreNotThirtyTwoBytes() {
        MicrosoftTokenCipher cipher = new MicrosoftTokenCipher(
                Base64.getEncoder().encodeToString(new byte[16]));

        assertThat(cipher.isConfigured()).isFalse();
    }
}
