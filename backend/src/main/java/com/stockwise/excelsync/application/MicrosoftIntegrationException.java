package com.stockwise.excelsync.application;

public class MicrosoftIntegrationException extends RuntimeException {
    private final boolean retryable;

    public MicrosoftIntegrationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public MicrosoftIntegrationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
