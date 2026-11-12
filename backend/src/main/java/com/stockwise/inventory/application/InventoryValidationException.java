package com.stockwise.inventory.application;

public class InventoryValidationException extends RuntimeException {
    private final String field;

    public InventoryValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
