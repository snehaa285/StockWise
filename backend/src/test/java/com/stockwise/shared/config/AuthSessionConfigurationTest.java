package com.stockwise.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AuthSessionConfigurationTest {
    private final AuthSessionConfiguration configuration = new AuthSessionConfiguration();

    @Test
    void csrfSecretMustBeAtLeast32BytesAndDifferFromJwtKey() {
        byte[] jwtBytes = new byte[32];
        byte[] csrfBytes = new byte[32];
        csrfBytes[0] = 1;
        SecretKey jwtKey = new SecretKeySpec(jwtBytes, "HmacSHA256");

        assertThatThrownBy(() -> configuration.csrfHmacKey(
                Base64.getEncoder().encodeToString(new byte[31]), jwtKey))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> configuration.csrfHmacKey(
                Base64.getEncoder().encodeToString(jwtBytes), jwtKey))
                .isInstanceOf(IllegalStateException.class);
        assertThat(configuration.csrfHmacKey(
                Base64.getEncoder().encodeToString(csrfBytes), jwtKey).getEncoded())
                .isEqualTo(csrfBytes);
    }

    @Test
    void crossSiteCookieRequiresSecureAndSameSiteMustBeExplicitlyValid() {
        assertThatThrownBy(() -> configuration.authCookieSettings(false, "None"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> configuration.authCookieSettings(false, "*"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(configuration.authCookieSettings(true, "None").secure()).isTrue();
    }
}
