package com.stockwise.auth.application;

public record VerifiedFirebaseIdentity(
        String firebaseUid,
        String email,
        boolean emailVerified,
        String displayName) {
}
