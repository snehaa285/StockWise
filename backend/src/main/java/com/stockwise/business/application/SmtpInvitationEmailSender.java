package com.stockwise.business.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${spring.mail.host:}'.trim().length() > 0")
public class SmtpInvitationEmailSender implements InvitationEmailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpInvitationEmailSender(
            JavaMailSender mailSender,
            @Value("${STOCKWISE_EMAIL_FROM}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String recipient, String businessName, String role, String invitationUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Invitation to join " + businessName + " on StockWise");
        message.setText("You have been invited to join "
                + businessName
                + " as "
                + role
                + ". Sign in or create a StockWise account using this email address, then accept "
                + "the invitation here:\n\n"
                + invitationUrl
                + "\n\nThis link expires in 7 days. If you did not expect this invitation, "
                + "you can ignore this message.");
        mailSender.send(message);
    }
}
