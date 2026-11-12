package com.stockwise.auth.api;

import com.stockwise.shared.config.AuthSessionConfiguration.AuthCookieSettings;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql")
public class AuthCookieService {
    public static final String REFRESH_COOKIE_NAME = "stockwise_refresh";
    public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthCookieSettings settings;

    public AuthCookieService(AuthCookieSettings settings) {
        this.settings = settings;
    }

    public ResponseCookie refreshCookie(String value, LocalDateTime expiresAt) {
        long maxAge = Math.max(0, Duration.between(
                LocalDateTime.now(ZoneOffset.UTC), expiresAt).getSeconds());
        return baseCookie(value)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clearRefreshCookie() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(settings.secure())
                .sameSite(settings.sameSite())
                .path(REFRESH_COOKIE_PATH);
    }
}
