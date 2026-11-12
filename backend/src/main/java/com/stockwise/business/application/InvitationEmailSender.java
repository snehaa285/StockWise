package com.stockwise.business.application;

public interface InvitationEmailSender {
    void send(String recipient, String businessName, String role, String invitationUrl);
}
