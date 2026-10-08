package com.lucidia.backend.consent;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.lucidia.backend.auth.User;
import com.lucidia.backend.auth.UserRepository;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * REST endpoints for consent management.
 *
 * <ul>
 *   <li>GET  /api/consent      — check if the current user has consented</li>
 *   <li>POST /api/consent      — record accept/decline decision</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/consent")
public class ConsentController {

    private final ConsentService consentService;
    private final UserRepository userRepository;

    public ConsentController(ConsentService consentService, UserRepository userRepository) {
        this.consentService = consentService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getConsentStatus(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = resolveUserId(jwt);
        boolean consented = consentService.hasConsented(userId);
        return ResponseEntity.ok(Map.of(
                "consented", consented,
                "version", ConsentService.CURRENT_VERSION
        ));
    }

    @PostMapping
    public ResponseEntity<?> recordConsent(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> body) {

        UUID userId = resolveUserId(jwt);
        boolean accepted = Boolean.TRUE.equals(body.get("accepted"));
        UserConsent consent = consentService.recordConsent(userId, accepted);
        return ResponseEntity.ok(Map.of(
                "accepted", consent.isAccepted(),
                "version", consent.getVersion(),
                "recordedAt", consent.getCreatedAt().toString()
        ));
    }

    private UUID resolveUserId(Jwt jwt) {
        if (jwt == null) throw new IllegalArgumentException("Authentication required");
        User user = userRepository.findByEmail(jwt.getSubject())
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        return user.getId();
    }
}
