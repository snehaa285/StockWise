package com.stockwise.shared.config;

import com.stockwise.auth.application.ActiveSessionValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

@Configuration
@Profile("mysql")
public class JwtConfiguration {
    @Bean
    SecretKey jwtSigningKey(@Value("${stockwise.auth.jwt.signing-secret}") String encodedSecret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "STOCKWISE_JWT_SIGNING_SECRET must be valid Base64 encoding at least 32 bytes.",
                    exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "STOCKWISE_JWT_SIGNING_SECRET must be valid Base64 encoding at least 32 bytes.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSigningKey,
            @Value("${stockwise.auth.jwt.issuer}") String issuer,
            @Value("${stockwise.auth.jwt.audience}") String audience,
            ActiveSessionValidator activeSessionValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerAndTimestamp =
                JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required audience is missing.", null));
        OAuth2TokenValidator<Jwt> sessionValidator = jwt -> {
            try {
                UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
                UUID userId = UUID.fromString(jwt.getSubject());
                return activeSessionValidator.isActive(sessionId, userId)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Session is not active.", null));
            } catch (IllegalArgumentException | NullPointerException exception) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Session claims are invalid.", null));
            }
        };
        decoder.setJwtValidator(jwt -> {
            OAuth2TokenValidatorResult standardResult = issuerAndTimestamp.validate(jwt);
            if (standardResult.hasErrors()) {
                return standardResult;
            }
            OAuth2TokenValidatorResult audienceResult = audienceValidator.validate(jwt);
            return audienceResult.hasErrors() ? audienceResult : sessionValidator.validate(jwt);
        });
        return decoder;
    }
}
