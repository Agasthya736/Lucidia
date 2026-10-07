package com.lucidia.backend.auth;

import com.lucidia.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtSecurityTest {

    @Test
    void testJwtConverterGrantsRoleClinicianByDefault() {
        SecurityConfig config = new SecurityConfig();
        var converter = config.jwtAuthenticationConverter();

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS512"),
                Map.of("sub", "clinician@lucidia.health", "userId", "123")
        );

        AbstractAuthenticationToken auth = converter.convert(jwt);
        assertNotNull(auth);
        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertTrue(authorities.contains("ROLE_CLINICIAN"));
    }

    @Test
    void testJwtConverterHandlesLegacyRoleClaim() {
        SecurityConfig config = new SecurityConfig();
        var converter = config.jwtAuthenticationConverter();

        // Legacy token with role: "ADMIN"
        Jwt adminJwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS512"),
                Map.of("sub", "admin@lucidia.health", "role", "ADMIN")
        );

        AbstractAuthenticationToken auth = converter.convert(adminJwt);
        assertNotNull(auth);
        List<String> authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertTrue(authorities.contains("ROLE_CLINICIAN"));
        assertTrue(authorities.contains("ROLE_ADMIN"));

        // Legacy token with role: "CLINICIAN"
        Jwt clinicianJwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "HS512"),
                Map.of("sub", "dr@lucidia.health", "role", "CLINICIAN")
        );

        AbstractAuthenticationToken clinAuth = converter.convert(clinicianJwt);
        assertNotNull(clinAuth);
        List<String> clinAuthorities = clinAuth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        assertTrue(clinAuthorities.contains("ROLE_CLINICIAN"));
    }

    @Test
    void testJwtServiceDoesNotIncludeRoleClaim() throws Exception {
        JwtService jwtService = new JwtService("a-very-long-secret-key-that-is-at-least-64-bytes-long-for-hs512-testing-purposes-1234567890", 60);
        User user = new User("Dr. Alice", "alice@lucidia.health", "hash");
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, UUID.randomUUID());

        String token = jwtService.generateToken(user);
        assertNotNull(token);

        var claims = io.jsonwebtoken.Jwts.parser()
                .verifyWith(jwtService.getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("alice@lucidia.health", claims.getSubject());
        assertNull(claims.get("role"));
        assertNotNull(claims.get("userId"));
    }
}
