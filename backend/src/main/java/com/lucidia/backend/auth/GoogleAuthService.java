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

@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final String configuredClientId;

    public GoogleAuthService(
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${lucidia.google.client-id:}") String configuredClientId
    ) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.configuredClientId = configuredClientId;
    }

    public AuthResponse authenticate(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new IllegalArgumentException("Google ID token cannot be empty");
        }

        // 1. Support dev / mock token for fast testing without Google Cloud credentials
        if (idTokenString.startsWith("mock_") || idTokenString.startsWith("dev_")) {
            log.info("Processing dev/mock Google sign-in token: {}", idTokenString);
            String mockEmail = idTokenString.contains("@")
                    ? idTokenString.replace("mock_", "").replace("dev_", "")
                    : "demo.clinician@lucidia.health";
            String mockName = "Dr. " + mockEmail.split("@")[0].replace(".", " ");
            return getOrCreateGoogleUser(mockEmail, mockName, null);
        }

        // 2. Real Google ID Token verification
        try {
            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            GoogleIdTokenVerifier.Builder builder = new GoogleIdTokenVerifier.Builder(transport, jsonFactory);
            if (configuredClientId != null && !configuredClientId.isBlank()) {
                builder.setAudience(Collections.singletonList(configuredClientId));
            }
            GoogleIdTokenVerifier verifier = builder.build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Google ID token verification failed (returned null)");
                throw new IllegalArgumentException("Invalid Google ID token");
            }

            Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified != null && !emailVerified) {
                throw new IllegalArgumentException("Google email is not verified");
            }

            String name = (String) payload.get("name");
            if (name == null || name.isBlank()) {
                name = email != null ? email.split("@")[0] : "Clinician";
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
