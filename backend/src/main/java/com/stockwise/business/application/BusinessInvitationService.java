package com.stockwise.business.application;

import com.stockwise.auth.application.EmailNormalizer;
import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.business.api.BusinessInvitationResponse;
import com.stockwise.business.api.CreateBusinessInvitationRequest;
import com.stockwise.business.domain.Business;
import com.stockwise.business.domain.BusinessInvitation;
import com.stockwise.business.domain.BusinessInvitationStatus;
import com.stockwise.business.domain.BusinessMembership;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.business.persistence.BusinessInvitationRepository;
import com.stockwise.business.persistence.BusinessMembershipRepository;
import com.stockwise.business.persistence.BusinessRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("mysql")
public class BusinessInvitationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final BusinessInvitationRepository invitationRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectProvider<InvitationEmailSender> emailSenderProvider;
    private final String publicUrl;

    public BusinessInvitationService(
            BusinessRepository businessRepository,
            BusinessMembershipRepository membershipRepository,
            BusinessInvitationRepository invitationRepository,
            AppUserRepository appUserRepository,
            ObjectProvider<InvitationEmailSender> emailSenderProvider,
            @Value("${stockwise.public-url:http://localhost:4173}") String publicUrl) {
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
        this.invitationRepository = invitationRepository;
        this.appUserRepository = appUserRepository;
        this.emailSenderProvider = emailSenderProvider;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public BusinessInvitationResponse invite(
            UUID businessId, UUID actorId, CreateBusinessInvitationRequest request) {
        Business business = businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(BusinessNotFoundException::new);
        requireOwner(businessId, actorId);
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessConflictException("Enter a valid invitation email address.");
        }
        if (request.role() == null || request.role() == BusinessRole.OWNER) {
            throw new BusinessConflictException("Invitations can only assign ADMIN or MEMBER.");
        }

        String email = EmailNormalizer.normalize(request.email());
        LocalDateTime now = now();
        var existing = invitationRepository.findByBusiness_IdAndEmailNormalizedAndStatus(
                businessId, email, BusinessInvitationStatus.PENDING);
        if (existing.isPresent()) {
            BusinessInvitation invitation = existing.get();
            if (invitation.getExpiresAt().isAfter(now)) {
                throw new BusinessConflictException(
                        "A pending invitation already exists for this address.");
            }
            invitation.markExpired();
        }

        AppUser actor = appUserRepository.findById(actorId)
                .orElseThrow(BusinessMemberNotFoundException::new);
        Token token = createToken();
        BusinessInvitation invitation = invitationRepository.saveAndFlush(
                BusinessInvitation.create(
                        business,
                        email,
                        request.role(),
                        token.hash(),
                        now.plusDays(7),
                        actor));
        sendInvitation(email, business.getName(), request.role(), token.raw());
        return BusinessInvitationResponse.from(invitation);
    }

    @Transactional(readOnly = true)
    public List<BusinessInvitationResponse> list(UUID businessId, UUID actorId) {
        requireOwner(businessId, actorId);
        return invitationRepository.findAllByBusiness_IdOrderByCreatedAtDesc(businessId).stream()
                .map(BusinessInvitationResponse::from)
                .toList();
    }

    @Transactional(noRollbackFor = BusinessConflictException.class)
    public void revoke(UUID businessId, UUID actorId, UUID invitationId) {
        businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(BusinessNotFoundException::new);
        requireOwner(businessId, actorId);
        BusinessInvitation invitation = invitationRepository
                .findByBusinessAndIdForUpdate(businessId, invitationId)
                .orElseThrow(BusinessMemberNotFoundException::new);
        expireIfNecessary(invitation);
        requirePending(invitation);
        invitation.revoke();
    }

    @Transactional(noRollbackFor = BusinessConflictException.class)
    public BusinessInvitationResponse resend(
            UUID businessId, UUID actorId, UUID invitationId) {
        Business business = businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(BusinessNotFoundException::new);
        requireOwner(businessId, actorId);
        BusinessInvitation invitation = invitationRepository
                .findByBusinessAndIdForUpdate(businessId, invitationId)
                .orElseThrow(BusinessMemberNotFoundException::new);
        expireIfNecessary(invitation);
        requirePending(invitation);
        Token token = createToken();
        invitation.replaceToken(token.hash(), now().plusDays(7));
        sendInvitation(
                invitation.getEmailNormalized(),
                business.getName(),
                invitation.getRole(),
                token.raw());
        return BusinessInvitationResponse.from(invitation);
    }

    @Transactional(noRollbackFor = BusinessConflictException.class)
    public void accept(UUID userId, String rawToken) {
        BusinessInvitation invitation = requireInvitation(rawToken);
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(BusinessMemberNotFoundException::new);
        verifyRecipientAndPending(invitation, user);
        if (membershipRepository.findByBusiness_IdAndUser_IdAndRemovedAtIsNull(
                invitation.getBusiness().getId(), userId).isPresent()) {
            throw invalidInvitation();
        }
        membershipRepository.save(BusinessMembership.create(
                invitation.getBusiness(), user, invitation.getRole()));
        invitation.accept();
    }

    @Transactional(noRollbackFor = BusinessConflictException.class)
    public void decline(UUID userId, String rawToken) {
        BusinessInvitation invitation = requireInvitation(rawToken);
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(BusinessMemberNotFoundException::new);
        verifyRecipientAndPending(invitation, user);
        invitation.decline();
    }

    private BusinessInvitation requireInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) {
            throw invalidInvitation();
        }
        return invitationRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(BusinessInvitationService::invalidInvitation);
    }

    private void verifyRecipientAndPending(BusinessInvitation invitation, AppUser user) {
        if (!invitation.getEmailNormalized().equals(user.getEmailNormalized())
                || !invitation.getStatus().equals(BusinessInvitationStatus.PENDING)) {
            throw invalidInvitation();
        }
        if (!invitation.getExpiresAt().isAfter(now())) {
            invitation.markExpired();
            throw invalidInvitation();
        }
    }

    private void expireIfNecessary(BusinessInvitation invitation) {
        if (invitation.getStatus() == BusinessInvitationStatus.PENDING
                && !invitation.getExpiresAt().isAfter(now())) {
            invitation.markExpired();
        }
    }

    private void requirePending(BusinessInvitation invitation) {
        if (invitation.getStatus() != BusinessInvitationStatus.PENDING) {
            throw new BusinessConflictException("This invitation is no longer pending.");
        }
    }

    private void requireOwner(UUID businessId, UUID actorId) {
        BusinessMembership membership = membershipRepository
                .findByBusiness_IdAndUser_IdAndRemovedAtIsNull(businessId, actorId)
                .orElseThrow(BusinessNotFoundException::new);
        if (membership.getRole() != BusinessRole.OWNER) {
            throw new BusinessForbiddenException();
        }
    }

    private void sendInvitation(
            String email, String businessName, BusinessRole role, String rawToken) {
        InvitationEmailSender sender = emailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Invitation email is not configured; no invitation was sent.");
        }
        String invitationUrl = publicUrl + "/invitation?token="
                + java.net.URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        try {
            sender.send(email, businessName, role.name(), invitationUrl);
        } catch (MailException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Invitation email could not be delivered.");
        }
    }

    private static Token createToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Token(raw, hash(raw));
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the runtime.", exception);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static BusinessConflictException invalidInvitation() {
        return new BusinessConflictException("This invitation link is invalid or expired.");
    }

    private record Token(String raw, String hash) {
    }
}
