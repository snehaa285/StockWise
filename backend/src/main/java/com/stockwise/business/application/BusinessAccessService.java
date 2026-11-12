package com.stockwise.business.application;

import com.stockwise.business.domain.BusinessMembership;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.business.persistence.BusinessMembershipRepository;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class BusinessAccessService {
    private final BusinessMembershipRepository membershipRepository;

    public BusinessAccessService(BusinessMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public BusinessMembership requireActiveMembership(UUID businessId, UUID userId) {
        return membershipRepository
                .findByBusiness_IdAndUser_IdAndRemovedAtIsNull(businessId, userId)
                .orElseThrow(BusinessNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public BusinessMembership requireRole(
            UUID businessId, UUID userId, BusinessRole... permittedRoles) {
        BusinessMembership membership = requireActiveMembership(businessId, userId);
        if (Arrays.stream(permittedRoles).noneMatch(role -> role == membership.getRole())) {
            throw new BusinessForbiddenException();
        }
        return membership;
    }
}
