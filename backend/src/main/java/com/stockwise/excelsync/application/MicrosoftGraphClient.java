package com.stockwise.excelsync.application;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Profile("mysql")
public class MicrosoftGraphClient {
    private final RestClient restClient;
    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public MicrosoftGraphClient(
            RestClient.Builder restClientBuilder,
            @Value("${stockwise.microsoft.tenant-id:}") String tenantId,
            @Value("${stockwise.microsoft.client-id:}") String clientId,
            @Value("${stockwise.microsoft.client-secret:}") String clientSecret,
            @Value("${stockwise.microsoft.redirect-uri:}") String redirectUri) {
        this.restClient = restClientBuilder.build();
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public boolean isConfigured() {
        return !tenantId.isBlank() && !clientId.isBlank()
                && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    public URI authorizationUri(String state, String codeChallenge) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(authorityBase() + "/oauth2/v2.0/authorize")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("response_mode", "query")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "offline_access User.Read Files.ReadWrite")
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUri();
    }

    public MicrosoftTokenResponse exchangeCode(String code, String verifier) {
        MultiValueMap<String, String> form = tokenForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("code_verifier", verifier);
        return tokenRequest(form, "Microsoft authorization could not be completed.");
    }

    public MicrosoftTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = tokenForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("scope", "offline_access User.Read Files.ReadWrite");
        return tokenRequest(form, "Microsoft authorization could not be refreshed.");
    }

    public MicrosoftAccountProfile getProfile(String accessToken) {
        try {
            MicrosoftAccountProfile profile = restClient.get()
                    .uri("https://graph.microsoft.com/v1.0/me?$select=id,mail,userPrincipalName")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.value() == 401, (request, response) -> {
                        throw new MicrosoftReauthorizationRequiredException();
                    })
                    .body(MicrosoftAccountProfile.class);
            if (profile == null || profile.id() == null || profile.id().isBlank()) {
                throw new MicrosoftIntegrationException(
                        "Microsoft did not return an account profile.", false);
            }
            return profile;
        } catch (MicrosoftReauthorizationRequiredException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw remoteFailure(exception, "Microsoft account lookup failed.");
        } catch (RestClientException exception) {
            throw new MicrosoftIntegrationException(
                    "Microsoft account lookup failed.", true, exception);
        }
    }

    public String uploadWorkbook(String accessToken, String driveItemId, String filename, byte[] content) {
        String path = driveItemId == null || driveItemId.isBlank()
                ? "https://graph.microsoft.com/v1.0/me/drive/root:/"
                        + encodePathSegment(filename) + ":/content"
                : "https://graph.microsoft.com/v1.0/me/drive/items/"
                        + encodePathSegment(driveItemId) + "/content";
        try {
            DriveItemResponse item = restClient.put()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(content)
                    .retrieve()
                    .onStatus(status -> status.value() == 401, (request, response) -> {
                        throw new MicrosoftReauthorizationRequiredException();
                    })
                    .body(DriveItemResponse.class);
            if (item == null || item.id() == null || item.id().isBlank()) {
                throw new MicrosoftIntegrationException(
                        "Microsoft did not return a workbook identifier.", true);
            }
            return item.id();
        } catch (MicrosoftReauthorizationRequiredException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw remoteFailure(exception, "Microsoft workbook synchronization failed.");
        } catch (RestClientException exception) {
            throw new MicrosoftIntegrationException(
                    "Microsoft workbook synchronization failed.", true, exception);
        }
    }

    private MicrosoftTokenResponse tokenRequest(
            MultiValueMap<String, String> form, String safeError) {
        requireConfigured();
        try {
            MicrosoftTokenResponse tokens = restClient.post()
                    .uri(authorityBase() + "/oauth2/v2.0/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MicrosoftTokenResponse.class);
            if (tokens == null || tokens.accessToken() == null || tokens.accessToken().isBlank()) {
                throw new MicrosoftIntegrationException(safeError, false);
            }
            return tokens;
        } catch (MicrosoftIntegrationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw remoteFailure(exception, safeError);
        } catch (RestClientException exception) {
            throw new MicrosoftIntegrationException(safeError, true, exception);
        }
    }

    private MultiValueMap<String, String> tokenForm() {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        return form;
    }

    private String authorityBase() {
        return "https://login.microsoftonline.com/" + encodePathSegment(tenantId);
    }

    private static String encodePathSegment(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new MicrosoftIntegrationException(
                    "Microsoft OneDrive integration is not configured.", false);
        }
    }

    private static MicrosoftIntegrationException remoteFailure(
            RestClientResponseException exception, String safeMessage) {
        int status = exception.getStatusCode().value();
        return new MicrosoftIntegrationException(
                safeMessage, status == 429 || status >= 500);
    }

    public record MicrosoftTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") String refreshToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresInSeconds) {
    }

    public record MicrosoftAccountProfile(
            String id,
            String mail,
            @com.fasterxml.jackson.annotation.JsonProperty("userPrincipalName")
            String userPrincipalName) {
        public String email() {
            return mail == null || mail.isBlank() ? userPrincipalName : mail;
        }
    }

    private record DriveItemResponse(String id) {
    }
}
