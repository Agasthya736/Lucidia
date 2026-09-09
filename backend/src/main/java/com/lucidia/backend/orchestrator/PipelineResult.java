package com.lucidia.backend.orchestrator;

import java.util.List;

import com.lucidia.backend.agents.verifier.VerificationResult;
import com.lucidia.backend.synthesis.GroundedReport;
import com.lucidia.backend.triage.AggregatedFindings;

public record PipelineResult(
        AggregatedFindings triage,
        GroundedReport report,
        VerificationResult verification,
        boolean isEscalated,
        List<String> warnings
) {}