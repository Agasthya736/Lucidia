package com.lucidia.backend.auth;

import com.google.cloud.storage.Storage;
import com.lucidia.backend.audit.AuditLogEntry;
import com.lucidia.backend.audit.AuditLogRepository;
import com.lucidia.backend.audit.AuditLogService;
import com.lucidia.backend.consent.UserConsent;
import com.lucidia.backend.consent.UserConsentRepository;
import com.lucidia.backend.feedback.ScanFeedback;
import com.lucidia.backend.feedback.ScanFeedbackRepository;
import com.lucidia.backend.scan.Scan;
import com.lucidia.backend.scan.ScanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class AccountDeletionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> "dummy-secret-key-that-is-long-enough-for-hs256-algorithm");
        registry.add("GEMINI_API_KEY", () -> "dummy-api-key");
        registry.add("GEMINI_MODEL", () -> "dummy-model");
        registry.add("GCS_BUCKET", () -> "dummy-bucket");
    }

    @MockitoBean
    private Storage storage;

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private UserConsentRepository consentRepository;

    @Autowired
    private ScanFeedbackRepository feedbackRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void testAccountDeletion() {
        // 1. Setup user
        User user = new User("test@example.com", "Test User", "https://example.com/avatar.jpg");
        user = userRepository.save(user);

        // 2. Setup scan
        Scan scan = new Scan(user.getId(), "test.jpg");
        scan = scanRepository.save(scan);

        // 3. Setup consent
        UserConsent consent = new UserConsent(user.getId(), true, "v1");
        consent = consentRepository.save(consent);

        // 4. Setup feedback
        ScanFeedback feedback = new ScanFeedback(scan.getId(), user.getId(), (short) 5, "Good");
        feedback = feedbackRepository.save(feedback);

        // 5. Setup audit log
        AuditLogEntry audit = new AuditLogEntry(user.getId(), "TEST_ACTION", scan.getId(), "prevHash");
        audit = auditLogRepository.save(audit);

        // Execute deletion
        accountDeletionService.deleteAccount(user.getId());

        // Verify user deleted
        assertFalse(userRepository.findById(user.getId()).isPresent());

        // Verify scan deleted
        assertTrue(scanRepository.findByUserId(user.getId()).isEmpty());

        // Verify consent deleted
        // ConsentRepository has no findByUserId so let's just count
        assertEquals(0, consentRepository.count());

        // Verify feedback deleted
        assertEquals(0, feedbackRepository.count());

        // Verify audit log retained and anonymised
        List<AuditLogEntry> auditLogs = auditLogRepository.findAll();
        assertFalse(auditLogs.isEmpty());
        AuditLogEntry retainedAudit = auditLogs.get(0);
        assertEquals(AuditLogService.ANONYMOUS_USER_ID, retainedAudit.getActorUserId());
    }
}