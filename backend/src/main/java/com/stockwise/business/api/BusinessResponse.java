package com.stockwise.business.api;

import com.stockwise.business.domain.Business;
import com.stockwise.business.domain.BusinessRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record BusinessResponse(
        UUID id, String name, BusinessRole role, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static BusinessResponse from(Business business, BusinessRole role) {
        return new BusinessResponse(
                business.getId(),
                business.getName(),
                role,
                business.getCreatedAt(),
                business.getUpdatedAt());
    }
}
