package com.stockwise.auth.api;

import com.stockwise.auth.application.LoginService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Profile("mysql")
public class LoginController {
    private final LoginService loginService;
    private final AuthCookieService authCookieService;

    public LoginController(
            LoginService loginService,
            AuthCookieService authCookieService) {
        this.loginService = loginService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(name = "User-Agent", required = false) String userAgent) {
        var result = loginService.login(request, userAgent);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, authCookieService.refreshCookie(
                        result.refreshToken(), result.refreshTokenExpiresAt()).toString())
                .body(result.response());
    }
}
