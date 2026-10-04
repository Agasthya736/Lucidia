package com.lucidia.backend.scan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucidia.backend.audit.AuditLogService;
import com.lucidia.backend.orchestrator.PipelineOrchestrator;
import com.lucidia.backend.orchestrator.PipelineResult;
import com.lucidia.backend.quota.QuotaService;
import com.lucidia.backend.triage.SliceInput;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final ScanRepository scanRepository;
    private final PipelineOrchestrator orchestrator;
    private final AuditLogService auditLogService;
    private final ImageStorageService imageStorageService;
    private final QuotaService quotaService;
    private final ScanDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScanService(
            ScanRepository scanRepository,
            PipelineOrchestrator orchestrator,
            AuditLogService auditLogService,
            ImageStorageService imageStorageService,
            QuotaService quotaService,
            ScanDeduplicationService deduplicationService) {
        this.scanRepository = scanRepository;
        this.orchestrator = orchestrator;
        this.auditLogService = auditLogService;
        this.imageStorageService = imageStorageService;
        this.quotaService = quotaService;
        this.deduplicationService = deduplicationService;
    }

    /**
     * Submit a multi-slice CT study or External Clinical Photo.
     */
    public Scan submit(UUID userId, List<SliceInput> slices, String modality, String clinicalNotes, String customApiKey) {
        if (slices == null || slices.isEmpty()) {
            throw new IllegalArgumentException("Cannot submit study with zero slices.");
        }

        String effectiveModality = (modality != null && !modality.isBlank()) ? modality.toUpperCase() : "CT_SERIES";
        boolean isByok = customApiKey != null && !customApiKey.isBlank();

        // 1. Check & consume quota (bypassed if BYOK)
        quotaService.checkAndConsume(userId, isByok);

        // 2. Hash-based Deduplication check
        String studyHash = deduplicationService.computeStudyHash(slices);
        Optional<Scan> cachedScan = deduplicationService.findExistingStudy(userId, studyHash);

        String primaryFilename = slices.get(0).filename();
        List<String> filenames = new ArrayList<>();
        for (SliceInput s : slices) filenames.add(s.filename());

        Scan scan = new Scan(userId, primaryFilename, slices.size(), studyHash);
        scan.setModality(effectiveModality);
        scan.setClinicalNotes(clinicalNotes);

        try {
            scan.setSliceFilenamesJson(objectMapper.writeValueAsString(filenames));
        } catch (Exception ignored) {}

        // If duplicate found in cache, reuse completed results
        if (cachedScan.isPresent()) {
            Scan prev = cachedScan.get();
            scan.setStatus(Scan.Status.COMPLETED);
            scan.setTriageJson(prev.getTriageJson());
            scan.setReportJson(prev.getReportJson());
            scan.setVerificationJson(prev.getVerificationJson());
            scan.setEscalated(prev.isEscalated());
            scan.setCompletedAt(Instant.now());
            scan = scanRepository.save(scan);

            // Save slice images
            for (int i = 0; i < slices.size(); i++) {
                imageStorageService.saveSlice(scan.getId(), i, slices.get(i).bytes());
            }

            auditLogService.record(userId, "SCAN_SUBMITTED_CACHE_HIT", scan.getId());
            return scan;
        }

        scan = scanRepository.save(scan);

        // Save slice images
        for (int i = 0; i < slices.size(); i++) {
            imageStorageService.saveSlice(scan.getId(), i, slices.get(i).bytes());
        }

        auditLogService.record(userId, "SCAN_SUBMITTED_" + effectiveModality, scan.getId());
        processAsync(scan.getId(), slices, effectiveModality, clinicalNotes, customApiKey);
        return scan;
    }

    public Scan submit(UUID userId, List<SliceInput> slices, String customApiKey) {
        return submit(userId, slices, "CT_SERIES", null, customApiKey);
    }

    /**
     * Backwards compatible single-image submit.
     */
    public Scan submit(UUID userId, String filename, byte[] imageBytes, String mimeType) {
        SliceInput slice = new SliceInput(0, filename, imageBytes, mimeType);
        return submit(userId, List.of(slice), "CT_SERIES", null, null);
    }

    @Async
    public void processAsync(UUID scanId, List<SliceInput> slices, String modality, String clinicalNotes, String customApiKey) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NoSuchElementException("Scan not found: " + scanId));

        scan.setStatus(Scan.Status.PROCESSING);
        scanRepository.save(scan);

        try {
            PipelineResult result = orchestrator.run(slices, modality, clinicalNotes, customApiKey);

            scan.setTriageJson(objectMapper.writeValueAsString(result.triage()));
            scan.setReportJson(objectMapper.writeValueAsString(result.report()));
            scan.setVerificationJson(objectMapper.writeValueAsString(result.verification()));
            scan.setEscalated(result.isEscalated());
            scan.setStatus(Scan.Status.COMPLETED);
            scan.setCompletedAt(Instant.now());
        } catch (Exception e) {
            log.error("Pipeline failed for scan {}: {}", scanId, e.getMessage(), e);
            scan.setStatus(Scan.Status.FAILED);
            scan.setErrorMessage(e.getMessage());
        }

        scanRepository.save(scan);
    }

    public void processAsync(UUID scanId, List<SliceInput> slices, String customApiKey) {
        processAsync(scanId, slices, "CT_SERIES", null, customApiKey);
    }

    public Scan get(UUID scanId, UUID requestingUserId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NoSuchElementException("Scan not found: " + scanId));
        if (!scan.getUserId().equals(requestingUserId)) {
            throw new SecurityException("Not authorized to view this scan");
        }
        return scan;
    }

    public List<Scan> listForUser(UUID userId) {
        return scanRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Mandatory clinician sign-off workflow.
     */
    public Scan finalizeScan(
            UUID scanId,
            UUID requestingUserId,
            String reviewerName,
            String reviewerCredentials,
            String signOffNotes) {

        if (reviewerName == null || reviewerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Clinician name is required for mandatory review sign-off.");
        }
        if (reviewerCredentials == null || reviewerCredentials.trim().isEmpty()) {
            throw new IllegalArgumentException("Clinician credentials / medical license info is required.");
        }

        Scan scan = get(scanId, requestingUserId);
        if (scan.getStatus() != Scan.Status.COMPLETED) {
            throw new IllegalStateException("Only completed scans can be finalized.");
        }

        scan.setStatus(Scan.Status.FINALIZED);
        scan.setReviewerName(reviewerName.trim());
        scan.setReviewerCredentials(reviewerCredentials.trim());
        scan.setSignOffNotes(signOffNotes != null ? signOffNotes.trim() : "");
        scan.setFinalizedAt(Instant.now());

        Scan saved = scanRepository.save(scan);
        auditLogService.record(requestingUserId, "SCAN_FINALIZED_BY_" + reviewerName.trim(), scanId);
        return saved;
    }

    public void delete(UUID scanId, UUID requestingUserId) {
        Scan scan = get(scanId, requestingUserId);
        scanRepository.delete(scan);
        auditLogService.record(requestingUserId, "SCAN_DELETED", scanId);
    }
}