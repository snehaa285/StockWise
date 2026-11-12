package com.stockwise.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class CsrfTokenServiceTest {
    private final SecretKey key =
            new SecretKeySpec(new byte[32], "HmacSHA256");
    private final CsrfTokenService csrfTokenService = new CsrfTokenService(key);

    @Test
    void tokenUsesApprovedPurposeAndIsBoundToOneSession() {
        UUID sessionId = UUID.randomUUID();
        String token = csrfTokenService.create(sessionId);

        assertThat(CsrfTokenService.PURPOSE).isEqualTo("stockwise:refresh-csrf:v1");
        assertThat(token).startsWith(sessionId + ".");
        assertThat(csrfTokenService.isValid(sessionId, token)).isTrue();
        assertThat(csrfTokenService.isValid(UUID.randomUUID(), token)).isFalse();
    }

    @Test
    void tamperedOrMalformedSignaturesAreRejected() {
        UUID sessionId = UUID.randomUUID();
        String token = csrfTokenService.create(sessionId);
        String[] parts = token.split("\\.");
        parts[1] = (parts[1].charAt(0) == 'A' ? "B" : "A") + parts[1].substring(1);

        assertThat(csrfTokenService.isValid(sessionId, String.join(".", parts))).isFalse();
        assertThat(csrfTokenService.isValid(sessionId, sessionId + ".not-base64!")).isFalse();
        assertThat(csrfTokenService.isValid(sessionId, null)).isFalse();
    }
}
