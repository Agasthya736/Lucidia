package com.lucidia.backend.auth;

import com.lucidia.backend.dto.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(409).body(new ErrorResponse("Email already registered"));
        }
        User user = new User(request.name(), request.email(), passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return ResponseEntity.status(201).body(new AuthResponse(null, user.getEmail(), user.getName()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(new ErrorResponse("Invalid email or password"));
        }
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token, user.getEmail(), user.getName()));
    }

    record ErrorResponse(String message) {}
}