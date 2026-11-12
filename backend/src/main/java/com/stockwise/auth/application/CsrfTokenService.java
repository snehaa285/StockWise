package com.stockwise.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mysql")
public class CsrfTokenService {
    public static final String PURPOSE = "stockwise:refresh-csrf:v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKey csrfHmacKey;

    public CsrfTokenService(@Qualifier("csrfHmacKey") SecretKey csrfHmacKey) {
        this.csrfHmacKey = csrfHmacKey;
    }

    public String create(UUID sessionId) {
        byte[] signature = sign(sessionId);
        return sessionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    public boolean isValid(UUID expectedSessionId, String token) {
        if (token == null) {
            return false;
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || !expectedSessionId.toString().equals(parts[0])) {
            return false;
        }

        try {
            byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[1]);
            return MessageDigest.isEqual(sign(expectedSessionId), suppliedSignature);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] sign(UUID sessionId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(csrfHmacKey);
            mac.update(PURPOSE.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return mac.doFinal(sessionId.toString().getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable.", exception);
        }
    }
}
