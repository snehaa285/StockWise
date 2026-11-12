package com.stockwise.auth.application;

public class FirebaseAccountLinkRequiredException extends RuntimeException {
    public FirebaseAccountLinkRequiredException() {
        super("This email already has a StockWise account. Sign in and link Google from account settings.");
    }
}
