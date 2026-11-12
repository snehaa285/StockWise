package com.stockwise.auth.application;

public interface FirebaseTokenVerifier {
    VerifiedFirebaseIdentity verify(String idToken);
}
