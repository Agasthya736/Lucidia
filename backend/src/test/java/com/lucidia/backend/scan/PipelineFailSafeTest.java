package com.lucidia.backend.scan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lucidia.backend.orchestrator.PipelineOrchestrator;
import com.lucidia.backend.triage.SliceInput;

class PipelineFailSafeTest {

    private ScanRepository scanRepository;
    private PipelineOrchestrator orchestrator;
    private AsyncPipelineExecutor executor;

    @BeforeEach
    void setUp() {
        scanRepository = Mockito.mock(ScanRepository.class);
        orchestrator = Mockito.mock(PipelineOrchestrator.class);
        executor = new AsyncPipelineExecutor(scanRepository, orchestrator);
    }

    @Test
    void testPipelineExceptionSetsScanFailedWithFailSafeMessage() {
        UUID scanId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Scan scan = new Scan(userId, "test.png");

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));
        when(scanRepository.save(any(Scan.class))).thenAnswer(inv -> inv.getArgument(0));

        // Simulate unexpected pipeline failure at any step (triage, synthesis, verification)
        when(orchestrator.run(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Inference runtime segmentation fault"));

        List<SliceInput> slices = List.of(new SliceInput(0, "test.png", new byte[]{1, 2, 3}, "image/png"));

        executor.execute(scanId, slices, "EXTERNAL_PHOTO", null, null);

        assertEquals(Scan.Status.FAILED, scan.getStatus(), "Failed pipeline must set status to FAILED");
        assertEquals("Something went wrong. Please try again, or see a doctor if you are concerned.",
                scan.getErrorMessage(),
                "Failed pipeline must display the public fail-safe message");

        verify(scanRepository, Mockito.atLeast(2)).save(scan);
    }

    @Test
    void testPipelineSuccessSetsScanCompletedAndFinalizedDirectly() {
        UUID scanId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Scan scan = new Scan(userId, "test.png");

        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));
        when(scanRepository.save(any(Scan.class))).thenAnswer(inv -> inv.getArgument(0));

        com.lucidia.backend.triage.AggregatedFindings triage =
                com.lucidia.backend.triage.AggregatedFindings.fromSliceFindings(List.of(), 0.70);
        com.lucidia.backend.synthesis.GroundedReport report =
                com.lucidia.backend.synthesis.GroundedReport.createCleanAutoSummary(triage);
        com.lucidia.backend.agents.verifier.VerificationResult verification =
                com.lucidia.backend.agents.verifier.VerificationResult.of(true, List.of(), "Verified", 1.0);
        com.lucidia.backend.orchestrator.PipelineResult result =
                new com.lucidia.backend.orchestrator.PipelineResult(triage, report, verification, false, List.of());

        when(orchestrator.run(any(), any(), any(), any())).thenReturn(result);

        List<SliceInput> slices = List.of(new SliceInput(0, "test.png", new byte[]{1, 2, 3}, "image/png"));

        executor.execute(scanId, slices, "CT_SERIES", null, null);

        assertEquals(Scan.Status.COMPLETED, scan.getStatus(), "Successful scan must be marked COMPLETED");
        assertNotNull(scan.getCompletedAt(), "completedAt must be set");
        assertNotNull(scan.getFinalizedAt(), "finalizedAt must be set directly without requiring clinician sign-off");

        verify(scanRepository, Mockito.atLeast(2)).save(scan);
    }
}
