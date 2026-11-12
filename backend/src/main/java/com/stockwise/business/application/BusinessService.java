package com.stockwise.business.application;

import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.business.api.BusinessMemberResponse;
import com.stockwise.business.api.BusinessResponse;
import com.stockwise.business.api.CreateBusinessRequest;
import com.stockwise.business.api.UpdateBusinessRequest;
import com.stockwise.business.domain.Business;
import com.stockwise.business.domain.BusinessMembership;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.business.persistence.BusinessMembershipRepository;
import com.stockwise.business.persistence.BusinessRepository;
import com.stockwise.excelsync.application.ExcelSyncQueueService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class BusinessService {
    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final AppUserRepository appUserRepository;
    private final ExcelSyncQueueService syncQueueService;

    public BusinessService(
            BusinessRepository businessRepository,
            BusinessMembershipRepository membershipRepository,
            AppUserRepository appUserRepository,
            ExcelSyncQueueService syncQueueService) {
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
        this.appUserRepository = appUserRepository;
        this.syncQueueService = syncQueueService;
    }

    @Transactional
    public BusinessResponse create(UUID creatorId, CreateBusinessRequest request) {
        AppUser creator = appUserRepository.findById(creatorId)
                .orElseThrow(BusinessMemberNotFoundException::new);
        Business business = businessRepository.save(Business.create(request.name(), creator));
        membershipRepository.save(
                BusinessMembership.create(business, creator, BusinessRole.OWNER));
        return BusinessResponse.from(business, BusinessRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<BusinessResponse> listForUser(UUID userId) {
        return membershipRepository
                .findAllByUser_IdAndRemovedAtIsNullOrderByCreatedAtAsc(userId)
                .stream()
                .map(membership -> BusinessResponse.from(
                        membership.getBusiness(), membership.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessResponse get(UUID businessId, UUID userId) {
        BusinessMembership membership = requireMembership(businessId, userId);
        return BusinessResponse.from(membership.getBusiness(), membership.getRole());
    }

    @Transactional
    public BusinessResponse update(
            UUID businessId, UUID actorId, UpdateBusinessRequest request) {
        Business business = lockBusiness(businessId);
        BusinessMembership actorMembership = requireMembership(businessId, actorId);
        requireOwner(actorMembership);
        business.rename(request.name());
        syncQueueService.enqueueChange(businessId, "BUSINESS");
        return BusinessResponse.from(business, actorMembership.getRole());
    }

    @Transactional(readOnly = true)
    public List<BusinessMemberResponse> listMembers(UUID businessId, UUID actorId) {
        BusinessMembership actorMembership = requireMembership(businessId, actorId);
        requireOwner(actorMembership);
        return membershipRepository
                .findAllByBusiness_IdAndRemovedAtIsNullOrderByCreatedAtAsc(businessId)
                .stream()
                .map(BusinessMemberResponse::from)
                .toList();
    }

    @Transactional
    public BusinessMemberResponse changeMemberRole(
            UUID businessId, UUID actorId, UUID memberId, BusinessRole role) {
        lockBusiness(businessId);
        requireOwner(requireMembership(businessId, actorId));
        if (actorId.equals(memberId) || role == BusinessRole.OWNER) {
            throw new BusinessConflictException(
                    "The owner cannot change their own role or assign ownership.");
        }

        BusinessMembership membership = activeMembershipForUpdate(businessId, memberId);
        if (membership.getRole() == BusinessRole.OWNER) {
            throw new BusinessConflictException("The business owner role cannot be changed.");
        }
        membership.changeRole(role);
        return BusinessMemberResponse.from(membership);
    }

    @Transactional
    public void removeMember(UUID businessId, UUID actorId, UUID memberId) {
        lockBusiness(businessId);
        requireOwner(requireMembership(businessId, actorId));
        if (actorId.equals(memberId)) {
            throw new BusinessConflictException("The business owner cannot remove themselves.");
        }

        BusinessMembership membership = activeMembershipForUpdate(businessId, memberId);
        if (membership.getRole() == BusinessRole.OWNER) {
            throw new BusinessConflictException("The business owner cannot be removed.");
        }
        membership.remove(LocalDateTime.now(ZoneOffset.UTC));
    }

    private Business lockBusiness(UUID businessId) {
        return businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(BusinessNotFoundException::new);
    }

    private BusinessMembership requireMembership(UUID businessId, UUID userId) {
        return membershipRepository
                .findByBusiness_IdAndUser_IdAndRemovedAtIsNull(businessId, userId)
                .orElseThrow(BusinessNotFoundException::new);
    }

    private BusinessMembership activeMembershipForUpdate(UUID businessId, UUID userId) {
        BusinessMembership membership = membershipRepository
                .findByBusinessAndUserForUpdate(businessId, userId)
                .filter(BusinessMembership::isActive)
                .orElseThrow(BusinessMemberNotFoundException::new);
        return membership;
    }

    private void requireOwner(BusinessMembership membership) {
        if (membership.getRole() != BusinessRole.OWNER) {
            throw new BusinessForbiddenException();
        }
    }
}
