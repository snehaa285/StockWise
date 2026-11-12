package com.stockwise.auth.application;

public class FirebaseIdentityConflictException extends RuntimeException {
    public FirebaseIdentityConflictException() {
        super("This Google identity is already linked to a different StockWise account.");
    }
}
