package com.stockwise.shared.config;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("mysql")
@EnableScheduling
public class AuthSessionConfiguration {
    @Bean
    SecretKey csrfHmacKey(
            @Value("${stockwise.auth.csrf.secret}") String encodedSecret,
            @Qualifier("jwtSigningKey") SecretKey jwtSigningKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "STOCKWISE_CSRF_HMAC_SECRET must be valid Base64 encoding at least 32 bytes.",
                    exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "STOCKWISE_CSRF_HMAC_SECRET must be valid Base64 encoding at least 32 bytes.");
        }
        if (MessageDigest.isEqual(keyBytes, jwtSigningKey.getEncoded())) {
            throw new IllegalStateException(
                    "STOCKWISE_CSRF_HMAC_SECRET must differ from STOCKWISE_JWT_SIGNING_SECRET.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    AuthCookieSettings authCookieSettings(
            @Value("${stockwise.auth.refresh-cookie.secure}") boolean secure,
            @Value("${stockwise.auth.refresh-cookie.same-site}") String sameSite) {
        if (!Set.of("Lax", "Strict", "None").contains(sameSite)) {
            throw new IllegalStateException(
                    "STOCKWISE_AUTH_REFRESH_COOKIE_SAME_SITE must be Lax, Strict, or None.");
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalStateException(
                    "The refresh cookie must be Secure when SameSite=None.");
        }
        return new AuthCookieSettings(secure, sameSite);
    }

    public record AuthCookieSettings(boolean secure, String sameSite) {
    }
}
