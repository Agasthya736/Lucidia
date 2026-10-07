package com.lucidia.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Authentication Data Transfer Objects for registration, login, and Google OAuth.
 * User authentication provides unified clinician credentials with no separate role field.
 */
public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String name,
            @Email @NotBlank String email,
            @Size(min = 8) String password
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record GoogleLoginRequest(
            @NotBlank String idToken
    ) {}

    public record AuthResponse(String token, String email, String name, String avatarUrl) {
        public AuthResponse(String token, String email, String name) {
            this(token, email, name, null);
        }
    }
}