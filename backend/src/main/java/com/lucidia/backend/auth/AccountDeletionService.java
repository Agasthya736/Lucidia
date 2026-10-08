package com.lucidia.backend.auth;

import com.lucidia.backend.audit.AuditLogRepository;
import com.lucidia.backend.consent.ConsentService;
import com.lucidia.backend.feedback.ScanFeedbackRepository;
import com.lucidia.backend.scan.ImageStorageService;
import com.lucidia.backend.scan.Scan;
import com.lucidia.backend.scan.ScanRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles the "Delete my account and data" flow required for GDPR/privacy
 * compliance and Play Store policy.
 *
 * <p>
 * Deletes in a single transaction:
 * <ol>
 * <li>GCS/local image files for every scan</li>
 * <li>scan_feedback rows</li>
 * <li>user_consents rows</li>
 * <li>scans rows</li>
 * <li>audit_log personal identifiers (anonymised, not deleted)</li>
 * <li>the user row itself</li>
 * </ol>
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final ScanRepository scanRepository;
    private final ImageStorageService imageStorageService;
    private final ConsentService consentService;
    private final ScanFeedbackRepository feedbackRepository;
    private final AuditLogRepository auditLogRepository;

    public AccountDeletionService(
            UserRepository userRepository,
            ScanRepository scanRepository,
            ImageStorageService imageStorageService,
            ConsentService consentService,
            ScanFeedbackRepository feedbackRepository,
            AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.scanRepository = scanRepository;
        this.imageStorageService = imageStorageService;
        this.consentService = consentService;
        this.feedbackRepository = feedbackRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Permanently deletes the user and all their data.
     * Audit-log rows are retained for legal purposes but the user_id and
     * ip_address columns are zeroed out.
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        log.info("Account deletion requested for userId={}", userId);

        // 1. Delete all GCS/local image slices (best-effort, outside the DB tx)
        List<Scan> scans = scanRepository.findByUserId(userId);
        for (Scan scan : scans) {
            try {
                imageStorageService.deleteAllSlicesForScan(scan.getId(), scan.getSliceCount());
            } catch (Exception e) {
                log.warn("Could not delete images for scanId={}: {}", scan.getId(), e.getMessage());
            }
        }

        // 2. Delete feedback
        feedbackRepository.deleteByUserId(userId);

        // 3. Delete consent records
        consentService.deleteForUser(userId);

        // 4. Delete scans (cascade will also delete scan_feedback via FK)
        scanRepository.deleteAll(scans);

        // 5. Anonymise audit log (retain rows, remove PII)
        auditLogRepository.anonymiseForUser(userId, com.lucidia.backend.audit.AuditLogService.ANONYMOUS_USER_ID);

        // 6. Delete the user row
        userRepository.deleteById(userId);

        log.info("Account deletion complete for userId={}", userId);
    }
}
