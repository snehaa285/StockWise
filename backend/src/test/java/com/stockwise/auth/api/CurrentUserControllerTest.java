package com.stockwise.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stockwise.auth.application.CurrentUserService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class CurrentUserControllerTest {
    @Mock
    private CurrentUserService currentUserService;

    @Test
    void missingProfileReturnsSafeNotFoundResponse() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.findById(userId)).thenReturn(Optional.empty());
        CurrentUserController controller = new CurrentUserController(currentUserService);

        ResponseEntity<?> response = controller.getCurrentUser(authentication(userId.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getDetail()).isEqualTo("User profile was not found.");
    }

    private JwtAuthenticationToken authentication(String subject) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claims(claims -> claims.putAll(Map.of(
                        "iss", "https://api.stockwise.example",
                        "aud", "stockwise-web")))
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
