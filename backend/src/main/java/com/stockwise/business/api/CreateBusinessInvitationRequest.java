package com.stockwise.business.api;

import com.stockwise.business.domain.BusinessRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBusinessInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull BusinessRole role) {
}
