package com.stockwise.excelsync.application;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql")
public class MicrosoftTokenCipher {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String encodedKey;

    public MicrosoftTokenCipher(
            @Value("${stockwise.microsoft.token-encryption-key:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public String encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + ciphertext.length)
                            .put(nonce)
                            .put(ciphertext)
                            .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Microsoft token encryption failed.", exception);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] envelope = Base64.getDecoder().decode(encrypted);
            if (envelope.length <= NONCE_LENGTH) {
                throw new IllegalStateException("Stored Microsoft token data is invalid.");
            }
            ByteBuffer input = ByteBuffer.wrap(envelope);
            byte[] nonce = new byte[NONCE_LENGTH];
            input.get(nonce);
            byte[] ciphertext = new byte[input.remaining()];
            input.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(
                    cipher.doFinal(ciphertext),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException("Stored Microsoft token data could not be decrypted.", exception);
        }
    }

    public boolean isConfigured() {
        return keyIsValid();
    }

    private SecretKeySpec key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedKey);
            if (bytes.length != 32) {
                throw new IllegalStateException(
                        "STOCKWISE_MICROSOFT_TOKEN_ENCRYPTION_KEY must be Base64 for 32 random bytes.");
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "STOCKWISE_MICROSOFT_TOKEN_ENCRYPTION_KEY must be Base64 for 32 random bytes.",
                    exception);
        }
    }

    private boolean keyIsValid() {
        try {
            key();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
}
