package com.lucidia.backend.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing authenticated user profile endpoints.
 */
@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getMe(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }

        User user = null;
        String userIdStr = jwt.getClaimAsString("userId");
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                user = userRepository.findById(UUID.fromString(userIdStr)).orElse(null);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (user == null && jwt.getSubject() != null) {
            user = userRepository.findByEmail(jwt.getSubject()).orElse(null);
        }

        if (user == null) {
            return ResponseEntity.status(404).body("User not found");
        }

        return ResponseEntity.ok(new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl()
        ));
    }

    public record UserProfileResponse(UUID id, String name, String email, String avatarUrl) {}
}
