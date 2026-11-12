package com.stockwise.auth.application;

import com.stockwise.auth.domain.AppUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
@Profile("mysql")
public class JwtTokenService {
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(10);

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${stockwise.auth.jwt.issuer}") String issuer,
            @Value("${stockwise.auth.jwt.audience}") String audience) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
    }

    public IssuedAccessToken issue(AppUser user, UUID sessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
        String tokenId = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(tokenId)
                .claim("sid", sessionId.toString())
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();

        return new IssuedAccessToken(token, ACCESS_TOKEN_TTL.toSeconds());
    }

    public record IssuedAccessToken(String value, long expiresInSeconds) {
    }
}
