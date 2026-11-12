package com.stockwise.auth.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenFactory {
    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedRefreshToken generate() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return new GeneratedRefreshToken(rawToken, sha256Hex(rawToken));
    }

    public String hash(String rawToken) {
        if (!isWellFormed(rawToken)) {
            throw new IllegalArgumentException("Refresh token format is invalid.");
        }
        return sha256Hex(rawToken);
    }

    public boolean isWellFormed(String rawToken) {
        return rawToken != null && rawToken.matches("[A-Za-z0-9_-]{43}");
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record GeneratedRefreshToken(String value, String hash) {
    }
}
