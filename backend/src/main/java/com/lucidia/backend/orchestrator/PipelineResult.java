package com.lucidia.backend.orchestrator;

import java.util.List;

import com.lucidia.backend.agents.arbiter.ArbitrationResult;
import com.lucidia.backend.agents.verifier.VerificationResult;
import com.lucidia.backend.agents.vision.VisionFindings;
import com.lucidia.backend.agents.writing.ReportDraft;

public record PipelineResult(
        VisionFindings visionA,
        VisionFindings visionB,
        ArbitrationResult arbitration,
        ReportDraft report,
        VerificationResult verification,
        boolean degraded,
        List<String> warnings
) {}