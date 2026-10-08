package com.lucidia.backend.consent;

import com.lucidia.backend.auth.User;
import com.lucidia.backend.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsentControllerTest {

    @Test
    void getConsentReturnsCurrentConsentStatus() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        UserRepository users = mock(UserRepository.class);
        ConsentService consents = mock(ConsentService.class);
        when(users.findByEmail("consent@example.com")).thenReturn(Optional.of(user));
        when(consents.hasConsented(userId)).thenReturn(true);
        ConsentController controller = new ConsentController(consents, users);

        ResponseEntity<?> response = controller.getConsentStatus(jwt());

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals(true, body.get("consented"));
        assertEquals(ConsentService.CURRENT_VERSION, body.get("version"));
    }

    private static User user(UUID userId) throws Exception {
        User user = new User("Consent User", "consent@example.com", "hash");
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, userId);
        return user;
    }

    private static Jwt jwt() {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS512"),
                Map.of("sub", "consent@example.com")
        );
    }
}
