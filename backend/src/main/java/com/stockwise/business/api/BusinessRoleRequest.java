package com.stockwise.business.api;

import com.stockwise.business.domain.BusinessRole;
import jakarta.validation.constraints.NotNull;

public record BusinessRoleRequest(@NotNull BusinessRole role) {
}
