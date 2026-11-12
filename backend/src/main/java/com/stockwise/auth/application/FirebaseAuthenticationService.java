package com.stockwise.auth.application;

import com.stockwise.auth.domain.AppUser;
import com.stockwise.auth.domain.AuthenticationMethod;
import com.stockwise.auth.domain.FirebaseIdentity;
import com.stockwise.auth.persistence.AppUserRepository;
import com.stockwise.auth.persistence.FirebaseIdentityRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class FirebaseAuthenticationService {
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_DISPLAY_NAME_LENGTH = 120;
    private static final int MAX_FIREBASE_UID_LENGTH = 128;

    private final FirebaseTokenVerifier tokenVerifier;
    private final AppUserRepository userRepository;
    private final FirebaseIdentityRepository identityRepository;
    private final AuthSessionService authSessionService;
    private final AuthLoginAuditService auditService;

    public FirebaseAuthenticationService(
            FirebaseTokenVerifier tokenVerifier,
            AppUserRepository userRepository,
            FirebaseIdentityRepository identityRepository,
            AuthSessionService authSessionService,
            AuthLoginAuditService auditService) {
        this.tokenVerifier = tokenVerifier;
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.authSessionService = authSessionService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthSessionService.LoginSessionResult login(String idToken, String userAgent) {
        VerifiedFirebaseIdentity verified = verify(idToken, null, userAgent);
        String normalizedEmail = normalizeAndValidateEmail(verified.email(), null, userAgent);

        Optional<FirebaseIdentity> linked =
                identityRepository.findByFirebaseUid(verified.firebaseUid());
        if (linked.isPresent()) {
            AppUser user = linked.get().getUser();
            if (!user.getEmailNormalized().equals(normalizedEmail)) {
                auditService.recordLoginFailure(
                        user, AuthenticationMethod.GOOGLE, userAgent);
                throw new FirebaseIdentityConflictException();
            }
            return authSessionService.createSession(
                    user, AuthenticationMethod.GOOGLE, userAgent);
        }

        Optional<AppUser> existing = userRepository.findByEmailNormalized(normalizedEmail);
        if (existing.isPresent()) {
            auditService.recordLoginFailure(
                    existing.get(), AuthenticationMethod.GOOGLE, userAgent);
            throw new FirebaseAccountLinkRequiredException();
        }

        AppUser user = userRepository.saveAndFlush(AppUser.registerFederated(
                displayName(verified.displayName(), normalizedEmail),
                normalizedEmail));
        identityRepository.saveAndFlush(FirebaseIdentity.link(
                user, verified.firebaseUid(), LocalDateTime.now(ZoneOffset.UTC)));
        return authSessionService.createSession(
                user, AuthenticationMethod.GOOGLE, userAgent);
    }

    @Transactional
    public void link(UUID userId, String idToken, String userAgent) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException());
        VerifiedFirebaseIdentity verified = verify(idToken, user, userAgent);
        String normalizedEmail = normalizeAndValidateEmail(
                verified.email(), user, userAgent);
        if (!user.getEmailNormalized().equals(normalizedEmail)) {
            auditService.recordLoginFailure(user, AuthenticationMethod.GOOGLE, userAgent);
            throw new FirebaseIdentityConflictException();
        }

        Optional<FirebaseIdentity> byUid =
                identityRepository.findByFirebaseUid(verified.firebaseUid());
        if (byUid.isPresent()) {
            if (byUid.get().getUser().getId().equals(userId)) {
                return;
            }
            auditService.recordLoginFailure(user, AuthenticationMethod.GOOGLE, userAgent);
            throw new FirebaseIdentityConflictException();
        }
        Optional<FirebaseIdentity> byUser = identityRepository.findByUser_Id(userId);
        if (byUser.isPresent()) {
            auditService.recordLoginFailure(user, AuthenticationMethod.GOOGLE, userAgent);
            throw new FirebaseIdentityConflictException();
        }

        identityRepository.saveAndFlush(FirebaseIdentity.link(
                user, verified.firebaseUid(), LocalDateTime.now(ZoneOffset.UTC)));
    }

    private VerifiedFirebaseIdentity verify(
            String idToken, AppUser knownUser, String userAgent) {
        VerifiedFirebaseIdentity verified;
        try {
            verified = tokenVerifier.verify(idToken);
        } catch (FirebaseTokenVerificationException exception) {
            auditService.recordLoginFailure(
                    knownUser, AuthenticationMethod.GOOGLE, userAgent);
            throw exception;
        } catch (FirebaseAuthenticationUnavailableException exception) {
            auditService.recordLoginFailure(
                    knownUser, AuthenticationMethod.GOOGLE, userAgent);
            throw exception;
        }

        if (verified == null
                || !verified.emailVerified()
                || verified.firebaseUid() == null
                || verified.firebaseUid().isBlank()
                || verified.firebaseUid().length() > MAX_FIREBASE_UID_LENGTH) {
            auditService.recordLoginFailure(
                    knownUser, AuthenticationMethod.GOOGLE, userAgent);
            throw new FirebaseTokenVerificationException();
        }
        return verified;
    }

    private String normalizeAndValidateEmail(
            String email, AppUser knownUser, String userAgent) {
        if (email == null
                || email.isBlank()
                || email.length() > MAX_EMAIL_LENGTH
                || !email.contains("@")) {
            auditService.recordLoginFailure(
                    knownUser, AuthenticationMethod.GOOGLE, userAgent);
            throw new FirebaseTokenVerificationException();
        }
        return EmailNormalizer.normalize(email);
    }

    private String displayName(String name, String normalizedEmail) {
        String safeName = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (safeName.isBlank()) {
            safeName = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        }
        return safeName.length() <= MAX_DISPLAY_NAME_LENGTH
                ? safeName : safeName.substring(0, MAX_DISPLAY_NAME_LENGTH);
    }
}
