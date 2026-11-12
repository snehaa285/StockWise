package com.stockwise.business.api;

import com.stockwise.business.domain.BusinessMembership;
import com.stockwise.business.domain.BusinessRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record BusinessMemberResponse(
        UUID userId,
        String displayName,
        String email,
        BusinessRole role,
        LocalDateTime memberSince) {
    public static BusinessMemberResponse from(BusinessMembership membership) {
        return new BusinessMemberResponse(
                membership.getUser().getId(),
                membership.getUser().getDisplayName(),
                membership.getUser().getEmailNormalized(),
                membership.getRole(),
                membership.getCreatedAt());
    }
}
