package com.stockwise.auth.application;

public class FirebaseTokenVerificationException extends RuntimeException {
    public FirebaseTokenVerificationException() {
        super("Google sign-in could not be verified.");
    }

    public FirebaseTokenVerificationException(Throwable cause) {
        super("Google sign-in could not be verified.", cause);
    }
}
