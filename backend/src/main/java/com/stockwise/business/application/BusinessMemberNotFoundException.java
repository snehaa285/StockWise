package com.stockwise.business.application;

public class BusinessMemberNotFoundException extends RuntimeException {
    public BusinessMemberNotFoundException() {
        super("Business member was not found.");
    }
}
