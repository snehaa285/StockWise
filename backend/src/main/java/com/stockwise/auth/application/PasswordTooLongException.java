package com.stockwise.auth.application;

public class PasswordTooLongException extends RuntimeException {
    public PasswordTooLongException() {
        super("Password must be no more than 72 UTF-8 bytes.");
    }
}
