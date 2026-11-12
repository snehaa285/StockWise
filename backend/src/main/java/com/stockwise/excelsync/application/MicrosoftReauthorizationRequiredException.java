package com.stockwise.excelsync.application;

public class MicrosoftReauthorizationRequiredException extends RuntimeException {
    public MicrosoftReauthorizationRequiredException() {
        super("Microsoft authorization must be renewed.");
    }
}
