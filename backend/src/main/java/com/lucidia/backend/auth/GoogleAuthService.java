package com.lucidia.backend.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.lucidia.backend.dto.AuthDtos.AuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Service for authenticating users via Google OAuth 2.0 ID tokens.
 * Creates or retrieves user records without role distinctions, issuing standard
 * JWTs with clinician-level platform access.
 */
@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);
    private static final String[] MOCK_PREFIXES = { "mock_", "dev_" };

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final String configuredClientId;
    private final boolean allowMock;

    public GoogleAuthService(
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${lucidia.google.client-id:}") String configuredClientId,
            @Value("${lucidia.auth.allow-mock:false}") boolean allowMock) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.configuredClientId = configuredClientId;
        this.allowMock = allowMock;
        if (allowMock) {
            log.warn("Mock Google sign-in is ENABLED (lucidia.auth.allow-mock=true). "
                    + "Never enable this on a publicly reachable deployment.");
        }
    }

    public AuthResponse authenticate(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new IllegalArgumentException("Google ID token cannot be empty");
        }

        // 1. Dev / mock tokens: only when explicitly enabled (local development)
        String mockPrefix = findMockPrefix(idTokenString);
        if (mockPrefix != null) {
            if (!allowMock) {
                log.warn("Rejected a dev/mock Google sign-in token because mock sign-in is disabled");
                throw new IllegalArgumentException("Invalid Google ID token");
            }
            String rest = idTokenString.substring(mockPrefix.length());
            String mockEmail = rest.contains("@") ? rest : "demo.clinician@lucidia.health";
            String mockName = "Dr. " + mockEmail.split("@")[0].replace(".", " ");
            log.info("Processing dev/mock Google sign-in");
            return getOrCreateGoogleUser(mockEmail, mockName, null);
        }

        // 2. Real Google ID token verification (requires a configured client ID)
        if (configuredClientId == null || configuredClientId.isBlank()) {
            log.error("Google sign-in rejected: lucidia.google.client-id is not configured");
            throw new IllegalArgumentException("Google sign-in is not configured");
        }

        try {
            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                    .setAudience(Collections.singletonList(configuredClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Google ID token verification failed (returned null)");
                throw new IllegalArgumentException("Invalid Google ID token");
            }

            Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Google token has no email");
            }
            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified != null && !emailVerified) {
                throw new IllegalArgumentException("Google email is not verified");
            }

            String name = (String) payload.get("name");
            if (name == null || name.isBlank()) {
                name = email.split("@")[0];
            }
            String pictureUrl = (String) payload.get("picture");

            return getOrCreateGoogleUser(email, name, pictureUrl);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error verifying Google ID token", e);
            throw new IllegalArgumentException("Failed to verify Google ID token: " + e.getMessage());
        }
    }

    private static String findMockPrefix(String token) {
        for (String prefix : MOCK_PREFIXES) {
            if (token.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    private AuthResponse getOrCreateGoogleUser(String email, String name, String pictureUrl) {
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            log.info("Creating new user from Google sign-in: email={}", email);
            User newUser = new User(name, email, null, "GOOGLE", pictureUrl);
            return userRepository.save(newUser);
        });

        String jwt = jwtService.generateToken(user);
        return new AuthResponse(jwt, user.getEmail(), user.getName(), user.getAvatarUrl());
    }
}