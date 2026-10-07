package com.lucidia.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserControllerTest {

    private UserRepository userRepository;
    private UserController userController;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        userController = new UserController(userRepository);
    }

    @Test
    void testGetMeReturnsUserProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User("Dr. Jane Doe", "jane@lucidia.health", "hash", "GOOGLE", "https://avatar.url/jane.png");
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS512"),
                Map.of("sub", "jane@lucidia.health", "userId", userId.toString())
        );

        ResponseEntity<?> response = userController.getMe(jwt);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof UserController.UserProfileResponse);

        UserController.UserProfileResponse body = (UserController.UserProfileResponse) response.getBody();
        assertEquals(userId, body.id());
        assertEquals("Dr. Jane Doe", body.name());
        assertEquals("jane@lucidia.health", body.email());
        assertEquals("https://avatar.url/jane.png", body.avatarUrl());
    }

    @Test
    void testGetMeFallsBackToEmailIfIdNotFound() {
        User user = new User("Dr. Fallback", "fallback@lucidia.health", "hash");
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail("fallback@lucidia.health")).thenReturn(Optional.of(user));

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS512"),
                Map.of("sub", "fallback@lucidia.health", "userId", UUID.randomUUID().toString())
        );

        ResponseEntity<?> response = userController.getMe(jwt);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void testGetMeReturns404WhenUserNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS512"),
                Map.of("sub", "missing@lucidia.health", "userId", UUID.randomUUID().toString())
        );

        ResponseEntity<?> response = userController.getMe(jwt);
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void testGetMeReturns401WhenJwtNull() {
        ResponseEntity<?> response = userController.getMe(null);
        assertEquals(401, response.getStatusCode().value());
    }
}
