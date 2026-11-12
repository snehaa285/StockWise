package com.stockwise.business.application;

public class BusinessNotFoundException extends RuntimeException {
    public BusinessNotFoundException() {
        super("Business was not found.");
    }
}
