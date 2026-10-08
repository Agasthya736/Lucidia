package com.lucidia.backend.auth;

import com.lucidia.backend.feedback.ScanFeedback;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Controller exposing authenticated user profile endpoints.
 *
 * <ul>
 *   <li>GET    /api/me            — profile (id, name, email, avatarUrl, languageCode)</li>
 *   <li>DELETE /api/me            — delete account and all data</li>
 *   <li>PUT    /api/me/retention  — update retention preference (days or null)</li>
 *   <li>PUT    /api/me/language   — update language preference</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;

    public UserController(UserRepository userRepository, AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping
    public ResponseEntity<?> getMe(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) return ResponseEntity.status(401).build();
        User user = resolve(jwt);
        if (user == null) return ResponseEntity.status(404).body("User not found");
        return ResponseEntity.ok(new UserProfileResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getAvatarUrl(), user.getLanguageCode(), user.getRetentionDays()
        ));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) return ResponseEntity.status(401).build();
        User user = resolve(jwt);
        if (user == null) return ResponseEntity.status(404).build();
        accountDeletionService.deleteAccount(user.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/retention")
    public ResponseEntity<?> updateRetention(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> body) {
        if (jwt == null) return ResponseEntity.status(401).build();
        User user = resolve(jwt);
        if (user == null) return ResponseEntity.status(404).build();
        Object val = body.get("retentionDays");
        user.setRetentionDays(val == null ? null : ((Number) val).intValue());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("retentionDays", String.valueOf(user.getRetentionDays())));
    }

    @PutMapping("/language")
    public ResponseEntity<?> updateLanguage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> body) {
        if (jwt == null) return ResponseEntity.status(401).build();
        User user = resolve(jwt);
        if (user == null) return ResponseEntity.status(404).build();
        String lang = String.valueOf(body.getOrDefault("languageCode", "en"));
        user.setLanguageCode(lang);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("languageCode", user.getLanguageCode()));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private User resolve(Jwt jwt) {
        String userIdStr = jwt.getClaimAsString("userId");
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                User found = userRepository.findById(UUID.fromString(userIdStr)).orElse(null);
                if (found != null) return found;
            } catch (IllegalArgumentException ignored) {}
        }
        // Fallback: look up by email (JWT subject)
        return jwt.getSubject() != null
                ? userRepository.findByEmail(jwt.getSubject()).orElse(null)
                : null;
    }

    public record UserProfileResponse(
            UUID id, String name, String email,
            String avatarUrl, String languageCode, Integer retentionDays) {}
}
