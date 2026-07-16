package com.verirad.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for VeriRad.
 *
 * - Stateless JWT-based auth (mobile clients hold the token, not a server session)
 * - Role-based access: CLINICIAN can submit scans and view reports they own;
 *   ADMIN can view audit logs.
 * - /api/auth/** is open (login/register); everything else requires a valid JWT.
 *
 * TODO: wire in actual JwtDecoder bean pointing at the auth provider once
 * the identity provider (self-hosted vs. OAuth2 IdP) is decided.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // stateless API, no cookies -> CSRF not applicable
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
