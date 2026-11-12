package com.stockwise.excelsync.application;

import com.stockwise.business.application.BusinessAccessService;
import com.stockwise.business.domain.BusinessRole;
import com.stockwise.excelsync.domain.MicrosoftConnectionStatus;
import com.stockwise.excelsync.domain.MicrosoftOAuthState;
import com.stockwise.excelsync.domain.MicrosoftOneDriveConnection;
import com.stockwise.excelsync.persistence.MicrosoftOAuthStateRepository;
import com.stockwise.excelsync.persistence.MicrosoftOneDriveConnectionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("mysql")
public class MicrosoftConnectionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final BusinessAccessService businessAccessService;
    private final MicrosoftGraphClient graphClient;
    private final MicrosoftTokenCipher tokenCipher;
    private final MicrosoftOAuthStateRepository oauthStateRepository;
    private final MicrosoftOneDriveConnectionRepository connectionRepository;
    private final ExcelSyncQueueService syncQueueService;
    private final String frontendUrl;

    public MicrosoftConnectionService(
            BusinessAccessService businessAccessService,
            MicrosoftGraphClient graphClient,
            MicrosoftTokenCipher tokenCipher,
            MicrosoftOAuthStateRepository oauthStateRepository,
            MicrosoftOneDriveConnectionRepository connectionRepository,
            ExcelSyncQueueService syncQueueService,
            @Value("${stockwise.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.businessAccessService = businessAccessService;
        this.graphClient = graphClient;
        this.tokenCipher = tokenCipher;
        this.oauthStateRepository = oauthStateRepository;
        this.connectionRepository = connectionRepository;
        this.syncQueueService = syncQueueService;
        this.frontendUrl = frontendUrl;
    }

    @Transactional
    public String beginAuthorization(UUID businessId, UUID actorId) {
        businessAccessService.requireRole(businessId, actorId, BusinessRole.OWNER);
        if (!graphClient.isConfigured() || !tokenCipher.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OneDrive is not configured. Contact the StockWise administrator.");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        oauthStateRepository.deleteAllByExpiresAtBefore(now);
        String state = randomUrlSafe(32);
        String verifier = randomUrlSafe(48);
        oauthStateRepository.save(MicrosoftOAuthState.create(
                businessId,
                actorId,
                sha256(state),
                tokenCipher.encrypt(verifier),
                now.plusMinutes(10)));
        return graphClient.authorizationUri(state, sha256Base64Url(verifier)).toString();
    }

    @Transactional(noRollbackFor = {
        MicrosoftIntegrationException.class,
        MicrosoftReauthorizationRequiredException.class,
        ResponseStatusException.class
    })
    public UUID completeAuthorization(String code, String rawState) {
        if (code == null || code.isBlank() || rawState == null || rawState.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Microsoft authorization response is incomplete.");
        }
        MicrosoftOAuthState state = oauthStateRepository
                .findByStateHashForUpdate(sha256(rawState))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Microsoft authorization state is invalid or expired."));
        if (!state.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            oauthStateRepository.delete(state);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Microsoft authorization state is invalid or expired.");
        }
        oauthStateRepository.delete(state);

        String verifier = tokenCipher.decrypt(state.getEncryptedCodeVerifier());
        MicrosoftGraphClient.MicrosoftTokenResponse tokens = graphClient.exchangeCode(code, verifier);
        if (tokens.refreshToken() == null || tokens.refreshToken().isBlank()) {
            throw new MicrosoftIntegrationException(
                    "Microsoft did not grant offline access. Reconnect OneDrive and approve access.",
                    false);
        }
        MicrosoftGraphClient.MicrosoftAccountProfile profile =
                graphClient.getProfile(tokens.accessToken());
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plusSeconds(Math.max(60, tokens.expiresInSeconds()));
        MicrosoftOneDriveConnection connection = connectionRepository
                .findByBusinessIdForUpdate(state.getBusinessId())
                .orElseGet(() -> MicrosoftOneDriveConnection.create(
                        state.getBusinessId(),
                        state.getUserId(),
                        profile.id(),
                        profile.email(),
                        tokenCipher.encrypt(tokens.accessToken()),
                        tokenCipher.encrypt(tokens.refreshToken()),
                        expiresAt,
                        null));

        if (connection.getMicrosoftAccountId() != null
                && !connection.getMicrosoftAccountId().equals(profile.id())) {
            connection.updateDriveItemId(null);
        }
        connection.connect(
                state.getUserId(),
                profile.id(),
                profile.email(),
                tokenCipher.encrypt(tokens.accessToken()),
                tokenCipher.encrypt(tokens.refreshToken()),
                expiresAt,
                connection.getDriveItemId());
        connectionRepository.save(connection);
        syncQueueService.enqueueResync(state.getBusinessId());
        return state.getBusinessId();
    }

    @Transactional
    public void disconnect(UUID businessId, UUID actorId) {
        businessAccessService.requireRole(businessId, actorId, BusinessRole.OWNER);
        connectionRepository.findByBusinessIdForUpdate(businessId)
                .ifPresent(connection -> {
                    connection.disconnect();
                    connectionRepository.save(connection);
                });
    }

    @Transactional(noRollbackFor = MicrosoftReauthorizationRequiredException.class)
    public String accessToken(UUID businessId) {
        MicrosoftOneDriveConnection connection = connectionRepository
                .findByBusinessIdForUpdate(businessId)
                .orElseThrow(MicrosoftReauthorizationRequiredException::new);
        if (connection.getAuthorizationStatus() != MicrosoftConnectionStatus.CONNECTED) {
            throw new MicrosoftReauthorizationRequiredException();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (connection.getAccessTokenExpiresAt() != null
                && connection.getAccessTokenExpiresAt().isAfter(now.plusMinutes(2))
                && connection.getEncryptedAccessToken() != null) {
            return tokenCipher.decrypt(connection.getEncryptedAccessToken());
        }
        if (connection.getEncryptedRefreshToken() == null) {
            connection.requireReauthorization();
            throw new MicrosoftReauthorizationRequiredException();
        }
        try {
            String refreshToken = tokenCipher.decrypt(connection.getEncryptedRefreshToken());
            MicrosoftGraphClient.MicrosoftTokenResponse tokens = graphClient.refresh(refreshToken);
            String nextRefreshToken = tokens.refreshToken() == null
                    || tokens.refreshToken().isBlank()
                    ? refreshToken
                    : tokens.refreshToken();
            connection.updateTokens(
                    tokenCipher.encrypt(tokens.accessToken()),
                    tokenCipher.encrypt(nextRefreshToken),
                    now.plusSeconds(Math.max(60, tokens.expiresInSeconds())));
            return tokens.accessToken();
        } catch (MicrosoftIntegrationException exception) {
            if (exception.isRetryable()) {
                throw exception;
            }
            connection.requireReauthorization();
            throw new MicrosoftReauthorizationRequiredException();
        }
    }

    @Transactional(noRollbackFor = MicrosoftReauthorizationRequiredException.class)
    public void markReauthorizationRequired(UUID businessId) {
        connectionRepository.findByBusinessIdForUpdate(businessId)
                .filter(connection ->
                        connection.getAuthorizationStatus() == MicrosoftConnectionStatus.CONNECTED)
                .ifPresent(MicrosoftOneDriveConnection::requireReauthorization);
    }

    @Transactional
    public void markSynchronized(UUID businessId, String driveItemId) {
        connectionRepository.findByBusinessIdForUpdate(businessId)
                .filter(connection ->
                        connection.getAuthorizationStatus() == MicrosoftConnectionStatus.CONNECTED)
                .ifPresent(connection -> {
                    connection.updateDriveItemId(driveItemId);
                    connection.markSynchronized();
                });
    }

    @Transactional(readOnly = true)
    public String driveItemId(UUID businessId) {
        return connectionRepository.findById(businessId)
                .filter(connection ->
                        connection.getAuthorizationStatus() == MicrosoftConnectionStatus.CONNECTED)
                .map(MicrosoftOneDriveConnection::getDriveItemId)
                .orElse(null);
    }

    public String callbackRedirect(String status, UUID businessId) {
        String redirect = frontendUrl.replaceAll("/+$", "") + "/?onedrive=" + status;
        return businessId == null ? redirect : redirect + "&businessId=" + businessId;
    }

    private static String randomUrlSafe(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String sha256Base64Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
