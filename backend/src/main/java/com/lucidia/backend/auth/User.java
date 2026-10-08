package com.lucidia.backend.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "auth_provider")
    private String authProvider = "LOCAL";

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** Null means keep data until the user explicitly deletes their account. */
    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "language_code", length = 10)
    private String languageCode = "en";

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected User() {
        // JPA
    }

    public User(String name, String email, String passwordHash) {
        this(name, email, passwordHash, "LOCAL", null);
    }

    public User(String name, String email, String passwordHash, String authProvider, String avatarUrl) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.authProvider = authProvider != null ? authProvider : "LOCAL";
        this.avatarUrl = avatarUrl;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getAuthProvider() { return authProvider; }
    public String getAvatarUrl() { return avatarUrl; }
    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer days) { this.retentionDays = days; }
    public String getLanguageCode() { return languageCode != null ? languageCode : "en"; }
    public void setLanguageCode(String code) { this.languageCode = code; }
    public Instant getCreatedAt() { return createdAt; }
}