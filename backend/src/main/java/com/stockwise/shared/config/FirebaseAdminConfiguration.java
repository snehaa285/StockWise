package com.stockwise.shared.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.stockwise.auth.application.FirebaseAuthenticationUnavailableException;
import com.stockwise.auth.application.FirebaseTokenVerificationException;
import com.stockwise.auth.application.FirebaseTokenVerifier;
import com.stockwise.auth.application.VerifiedFirebaseIdentity;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("mysql")
public class FirebaseAdminConfiguration {
    @Bean
    FirebaseTokenVerifier firebaseTokenVerifier(
            @Value("${stockwise.firebase.project-id:}") String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return token -> {
                throw new FirebaseAuthenticationUnavailableException();
            };
        }

        FirebaseApp firebaseApp = firebaseApp(projectId);
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance(firebaseApp);
        return idToken -> verify(firebaseAuth, idToken);
    }

    private FirebaseApp firebaseApp(String projectId) {
        try {
            return FirebaseApp.initializeApp(
                    FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.getApplicationDefault())
                            .setProjectId(projectId)
                            .build(),
                    "stockwise");
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalStateException(
                    "Firebase Admin configuration could not be initialized from application credentials.",
                    exception);
        }
    }

    private VerifiedFirebaseIdentity verify(FirebaseAuth firebaseAuth, String idToken) {
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
            Map<String, Object> claims = decoded.getClaims();
            return new VerifiedFirebaseIdentity(
                    decoded.getUid(),
                    stringClaim(claims, "email"),
                    Boolean.TRUE.equals(claims.get("email_verified")),
                    stringClaim(claims, "name"));
        } catch (FirebaseAuthException | IllegalArgumentException exception) {
            throw new FirebaseTokenVerificationException(exception);
        }
    }

    private String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof String text ? text : null;
    }
}
