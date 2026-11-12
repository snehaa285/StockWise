package com.stockwise.auth.application;

public class FirebaseAuthenticationUnavailableException extends RuntimeException {
    public FirebaseAuthenticationUnavailableException() {
        super("Google sign-in is not configured for this environment.");
    }
}
