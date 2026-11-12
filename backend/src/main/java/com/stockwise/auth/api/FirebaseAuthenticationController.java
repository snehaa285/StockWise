package com.stockwise.auth.api;

import com.stockwise.auth.application.AuthSessionService;
import com.stockwise.auth.application.FirebaseAuthenticationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/firebase")
@Profile("mysql")
public class FirebaseAuthenticationController {
    private final FirebaseAuthenticationService firebaseAuthenticationService;
    private final AuthCookieService cookieService;

    public FirebaseAuthenticationController(
            FirebaseAuthenticationService firebaseAuthenticationService,
            AuthCookieService cookieService) {
        this.firebaseAuthenticationService = firebaseAuthenticationService;
        this.cookieService = cookieService;
    }

    @PostMapping
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody FirebaseIdTokenRequest request,
            @RequestHeader(name = "User-Agent", required = false) String userAgent) {
        AuthSessionService.LoginSessionResult result =
                firebaseAuthenticationService.login(request.idToken(), userAgent);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookieService.refreshCookie(
                        result.refreshToken(), result.refreshTokenExpiresAt()).toString())
                .body(result.response());
    }

    @PostMapping("/link")
    public ResponseEntity<Void> link(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody FirebaseIdTokenRequest request,
            @RequestHeader(name = "User-Agent", required = false) String userAgent) {
        UUID userId = UUID.fromString(authentication.getName());
        firebaseAuthenticationService.link(userId, request.idToken(), userAgent);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
