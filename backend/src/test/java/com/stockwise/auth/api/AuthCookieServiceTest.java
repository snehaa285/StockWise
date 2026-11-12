package com.stockwise.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockwise.shared.config.AuthSessionConfiguration.AuthCookieSettings;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthCookieServiceTest {
    @Test
    void refreshAndClearingCookiesUseMatchingSecurityAttributes() {
        AuthCookieService service = new AuthCookieService(new AuthCookieSettings(true, "Lax"));
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusDays(1);

        String refreshCookie = service.refreshCookie("opaque-test-value", expiresAt).toString();
        String clearCookie = service.clearRefreshCookie().toString();

        assertThat(refreshCookie)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/api/v1/auth");
        assertThat(clearCookie)
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=0");
    }
}
