package com.stockwise.auth.api;

import com.stockwise.auth.domain.AppUser;
import java.util.UUID;

public record UserResponse(UUID id, String displayName, String email) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getDisplayName(), user.getEmailNormalized());
    }
}
