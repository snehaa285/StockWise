package com.stockwise.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirebaseIdTokenRequest(
        @NotBlank @Size(max = 8192) String idToken) {
}
