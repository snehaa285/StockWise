package com.stockwise.business.api;

import com.stockwise.business.domain.BusinessInvitation;
import com.stockwise.business.domain.BusinessInvitationStatus;
import com.stockwise.business.domain.BusinessRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record BusinessInvitationResponse(
        UUID id,
        String email,
        BusinessRole role,
        BusinessInvitationStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {
    public static BusinessInvitationResponse from(BusinessInvitation invitation) {
        return new BusinessInvitationResponse(
                invitation.getId(),
                invitation.getEmailNormalized(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt());
    }
}
