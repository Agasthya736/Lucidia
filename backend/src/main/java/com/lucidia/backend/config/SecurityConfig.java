package com.lucidia.backend.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import com.lucidia.backend.auth.JwtService;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class SecurityConfig {

    /**
     * Paths that are allowed to carry the JWT as a {@code ?token=} URI query parameter.
     * All other endpoints must use the {@code Authorization: Bearer} header.
     * Restrict to image-serving routes only, to prevent token leakage via server-side logs
     * on general API calls.
     */
    private static final Set<String> TOKEN_PARAM_ALLOWED_PATH_PREFIXES = Set.of(
            "/api/scans/",   // covers /{id}/image, /{id}/slices/*, /{id}/report.pdf
            "/api/me"        // profile image fetches (if added later)
    );

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/test/**").permitAll()
                .requestMatchers("/api/scans/**").hasRole("CLINICIAN")
                .requestMatchers("/api/consent/**").hasRole("CLINICIAN")
                .requestMatchers("/api/me/**").hasRole("CLINICIAN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .bearerTokenResolver(bearerTokenResolver())
            );

        return http.build();
    }

    /**
     * Checks {@code Authorization: Bearer} header first. Only falls back to {@code ?token=}
     * query parameter for image-serving paths (where embedding tokens in URLs is unavoidable).
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
        headerResolver.setAllowFormEncodedBodyParameter(false);
        headerResolver.setAllowUriQueryParameter(false);

        DefaultBearerTokenResolver uriResolver = new DefaultBearerTokenResolver();
        uriResolver.setAllowUriQueryParameter(true);
        uriResolver.setAllowFormEncodedBodyParameter(false);

        return (HttpServletRequest request) -> {
            // Always try header first
            String fromHeader = headerResolver.resolve(request);
            if (fromHeader != null) {
                return fromHeader;
            }
            // Allow URI ?token= only on specific paths
            String path = request.getRequestURI();
            boolean tokenParamAllowed = TOKEN_PARAM_ALLOWED_PATH_PREFIXES.stream()
                    .anyMatch(path::startsWith);
            if (tokenParamAllowed) {
                return uriResolver.resolve(request);
            }
            return null;
        };
    }

    /**
     * Grants every authenticated user the fixed authority ROLE_CLINICIAN.
     * Legacy tokens containing a role claim continue to work seamlessly.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_CLINICIAN"));

            String role = jwt.getClaimAsString("role");
            if (role != null && !role.isBlank()) {
                String roleAuthority = "ROLE_" + role.trim();
                if (!"ROLE_CLINICIAN".equalsIgnoreCase(roleAuthority)) {
                    authorities.add(new SimpleGrantedAuthority(roleAuthority));
                }
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return NimbusJwtDecoder
                .withSecretKey(jwtService.getKey())
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }
}