package com.stockwise.auth.api;

import com.stockwise.auth.application.AuthSessionService;
import com.stockwise.auth.application.RefreshTransactionService;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("mysql")
public class AuthSessionController {
    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private final AuthSessionService authSessionService;
    private final AuthCookieService authCookieService;

    public AuthSessionController(
            AuthSessionService authSessionService,
            AuthCookieService authCookieService) {
        this.authSessionService = authSessionService;
        this.authCookieService = authCookieService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<?> csrf(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false)
                    String refreshToken) {
        Optional<String> csrfToken = authSessionService.csrfToken(refreshToken);
        if (csrfToken.isEmpty()) {
            return unauthorized();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(csrfToken.get()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false)
                    String refreshToken,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfToken) {
        RefreshTransactionService.RefreshOutcome outcome =
                authSessionService.refresh(refreshToken, csrfToken);
        if (outcome.status() != RefreshTransactionService.RefreshOutcome.Status.ROTATED) {
            return unauthorized();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Set-Cookie", authCookieService.refreshCookie(
                        outcome.refreshToken(), outcome.refreshTokenExpiresAt()).toString())
                .body(authSessionService.toResponse(outcome));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = AuthCookieService.REFRESH_COOKIE_NAME, required = false)
                    String refreshToken,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfToken) {
        if (!authSessionService.logout(refreshToken, csrfToken)) {
            return unauthorized();
        }
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header("Set-Cookie", authCookieService.clearRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<ProblemDetail> unauthorized() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Refresh authentication failed.");
        problem.setTitle("Authentication failed");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .header("Set-Cookie", authCookieService.clearRefreshCookie().toString())
                .body(problem);
    }
}
