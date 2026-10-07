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
    private GoogleAuthService mockEnabledService;
    private GoogleAuthService mockDisabledService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        jwtService = Mockito.mock(JwtService.class);
        mockEnabledService = new GoogleAuthService(userRepository, jwtService, "test-client-id", true);
        mockDisabledService = new GoogleAuthService(userRepository, jwtService, "test-client-id", false);
    }

    @Test
    void testBlankTokenThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> mockEnabledService.authenticate(""));
        assertThrows(IllegalArgumentException.class, () -> mockEnabledService.authenticate(null));
    }

    @Test
    void testDevMockTokenGeneratesUserAndJwt() {
        when(userRepository.findByEmail("test.doctor@lucidia.health")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("mock-jwt-token-123");

        AuthResponse response = mockEnabledService.authenticate("mock_test.doctor@lucidia.health");

        assertNotNull(response);
        assertEquals("mock-jwt-token-123", response.token());
        assertEquals("test.doctor@lucidia.health", response.email());
        assertEquals("test doctor", response.name());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testDevMockTokenExistingUser() {
        User existingUser = new User("Dr. Existing", "existing@lucidia.health", null, "GOOGLE", null);
        when(userRepository.findByEmail("existing@lucidia.health")).thenReturn(Optional.of(existingUser));
        when(jwtService.generateToken(existingUser)).thenReturn("existing-jwt-token");

        AuthResponse response = mockEnabledService.authenticate("mock_existing@lucidia.health");

        assertNotNull(response);
        assertEquals("existing-jwt-token", response.token());
        assertEquals("existing@lucidia.health", response.email());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testMockTokenRejectedWhenMockDisabled() {
        assertThrows(IllegalArgumentException.class,
                () -> mockDisabledService.authenticate("mock_existing@lucidia.health"));
        verifyNoInteractions(userRepository, jwtService);
    }

    @Test
    void testDevPrefixTokenRejectedWhenMockDisabled() {
        assertThrows(IllegalArgumentException.class,
                () -> mockDisabledService.authenticate("dev_anyone@lucidia.health"));
        verifyNoInteractions(userRepository, jwtService);
    }

    @Test
    void testRealTokenRejectedWhenClientIdNotConfigured() {
        GoogleAuthService unconfigured = new GoogleAuthService(userRepository, jwtService, "", false);
        assertThrows(IllegalArgumentException.class,
                () -> unconfigured.authenticate("header.payload.signature"));
        verifyNoInteractions(userRepository, jwtService);
    }

    @Test
    void testInvalidTokenThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> mockDisabledService.authenticate("invalid_non_mock_token_string"));
    }
}