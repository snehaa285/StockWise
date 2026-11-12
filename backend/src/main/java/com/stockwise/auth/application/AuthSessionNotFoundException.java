package com.stockwise.auth.application;

public class AuthSessionNotFoundException extends RuntimeException {
    public AuthSessionNotFoundException() {
        super("Authentication session was not found.");
    }
}
