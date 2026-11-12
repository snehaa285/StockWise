package com.stockwise.auth.api;

public record RefreshResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds) {
}
