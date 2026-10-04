package com.lucidia.backend.auth;

import com.lucidia.backend.dto.AuthDtos.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoogleAuthServiceTest {

    private UserRepository userRepository;
    private JwtService jwtService;
    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        jwtService = Mockito.mock(JwtService.class);
        googleAuthService = new GoogleAuthService(userRepository, jwtService, "test-client-id");
    }

    @Test
    void testBlankTokenThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> googleAuthService.authenticate(""));
        assertThrows(IllegalArgumentException.class, () -> googleAuthService.authenticate(null));
    }

    @Test
    void testDevMockTokenGeneratesUserAndJwt() {
        when(userRepository.findByEmail("test.doctor@lucidia.health")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token-123");

        AuthResponse response = googleAuthService.authenticate("mock_test.doctor@lucidia.health");

        assertNotNull(response);
        assertEquals("mock-jwt-token-123", response.token());
        assertEquals("test.doctor@lucidia.health", response.email());
        assertEquals("Dr. test doctor", response.name());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testDevMockTokenExistingUser() {
        User existingUser = new User("Dr. Existing", "existing@lucidia.health", null, "GOOGLE", null);
        when(userRepository.findByEmail("existing@lucidia.health")).thenReturn(Optional.of(existingUser));
        when(jwtService.generateToken(existingUser)).thenReturn("existing-jwt-token");

        AuthResponse response = googleAuthService.authenticate("mock_existing@lucidia.health");

        assertNotNull(response);
        assertEquals("existing-jwt-token", response.token());
        assertEquals("existing@lucidia.health", response.email());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testInvalidTokenThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> googleAuthService.authenticate("invalid_non_mock_token_string"));
    }
}
