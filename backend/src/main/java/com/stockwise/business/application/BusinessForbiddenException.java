package com.stockwise.business.application;

public class BusinessForbiddenException extends RuntimeException {
    public BusinessForbiddenException() {
        super("You are not permitted to perform this business operation.");
    }
}
