package com.lucidia.backend.scan;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucidia.backend.orchestrator.PipelineOrchestrator;
import com.lucidia.backend.orchestrator.PipelineResult;

@Service
public class ScanService {

    private final ScanRepository scanRepository;
    private final PipelineOrchestrator orchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScanService(ScanRepository scanRepository, PipelineOrchestrator orchestrator) {
        this.scanRepository = scanRepository;
        this.orchestrator = orchestrator;
    }

    public Scan submit(UUID userId, String filename, byte[] imageBytes, String mimeType) {
        Scan scan = new Scan(userId, filename);
        scan = scanRepository.save(scan);
        processAsync(scan.getId(), imageBytes, mimeType);
        return scan;
    }

    @Async
    public void processAsync(UUID scanId, byte[] imageBytes, String mimeType) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NoSuchElementException("Scan not found: " + scanId));

        scan.setStatus(Scan.Status.PROCESSING);
        scanRepository.save(scan);

        try {
            PipelineResult result = orchestrator.run(imageBytes, mimeType);

            scan.setVisionAJson(objectMapper.writeValueAsString(result.visionA()));
            scan.setVisionBJson(objectMapper.writeValueAsString(result.visionB()));
            scan.setArbitrationJson(objectMapper.writeValueAsString(result.arbitration()));
            scan.setReportJson(objectMapper.writeValueAsString(result.report()));
            scan.setVerificationJson(objectMapper.writeValueAsString(result.verification()));
            scan.setStatus(Scan.Status.COMPLETED);
            scan.setCompletedAt(Instant.now());
        } catch (Exception e) {
            scan.setStatus(Scan.Status.FAILED);
            scan.setErrorMessage(e.getMessage());
        }

        scanRepository.save(scan);
    }

    public Scan get(UUID scanId, UUID requestingUserId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NoSuchElementException("Scan not found"));
        if (!scan.getUserId().equals(requestingUserId)) {
            throw new SecurityException("Not authorized to view this scan");
        }
        return scan;
    }

    public List<Scan> listForUser(UUID userId) {
        return scanRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Scan finalizeScan(UUID scanId, UUID requestingUserId) {
        Scan scan = get(scanId, requestingUserId);
        if (scan.getStatus() != Scan.Status.COMPLETED) {
            throw new IllegalStateException("Only completed scans can be finalized");
        }
        scan.setStatus(Scan.Status.FINALIZED);
        scan.setFinalizedAt(Instant.now());
        return scanRepository.save(scan);
    }
}