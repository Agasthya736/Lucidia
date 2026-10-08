package com.lucidia.backend;

import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class LucidiaApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("JWT_SECRET", () -> "dummy-secret-key-that-is-long-enough-for-hs256-algorithm");
        registry.add("LUCIDIA_JWT_SECRET", () -> "dummy-secret-key-that-is-long-enough-for-hs256-algorithm");
        registry.add("GEMINI_API_KEY", () -> "dummy-api-key");
        registry.add("GEMINI_MODEL", () -> "dummy-model");
        registry.add("GCS_BUCKET", () -> "dummy-bucket");
    }

    @MockitoBean
    private Storage storage;

    @Test
    void contextLoads() {
    }
}