package com.lucidia.backend.scan;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucidia.backend.orchestrator.PipelineOrchestrator;
import com.lucidia.backend.orchestrator.PipelineResult;
import com.lucidia.backend.triage.SliceInput;

/**
 * Dedicated executor for the async AI pipeline.
 * Extracted into a separate Spring bean so that {@code @Async} is applied
 * through the Spring proxy — self-invocation on {@code ScanService} would
 * bypass the proxy and run synchronously.
 */
@Service
public class AsyncPipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(AsyncPipelineExecutor.class);

    private final ScanRepository scanRepository;
    private final PipelineOrchestrator orchestrator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AsyncPipelineExecutor(ScanRepository scanRepository, PipelineOrchestrator orchestrator) {
        this.scanRepository = scanRepository;
        this.orchestrator = orchestrator;
    }

    /**
     * Runs the full AI triage pipeline on a background thread.
     * On any exception the scan is marked {@code FAILED} and the error message is persisted.
     */
    @Async
    public void execute(java.util.UUID scanId, List<SliceInput> slices, String modality, String clinicalNotes, String customApiKey) {
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
            // Truncate very long stack-trace messages to avoid DB column overflow
            String msg = e.getMessage();
            if (msg != null && msg.length() > 2000) {
                msg = msg.substring(0, 2000) + "…";
            }
            scan.setErrorMessage(msg != null ? msg : e.getClass().getSimpleName());
        }

        scanRepository.save(scan);
    }
}
