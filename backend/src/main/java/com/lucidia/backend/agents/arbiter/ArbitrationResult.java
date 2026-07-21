package com.lucidia.backend.agents.arbiter;

import java.util.List;

/**
 * Output of comparing two independent VisionFindings.
 * This is what gets written into PipelineState after arbitration.
 */
public record ArbitrationResult(
        boolean agree,
        double agreementScore,       // 0.0-1.0, how much overlap between the two agents' findings
        List<String> sharedObservations,
        List<String> conflictingObservations, // present in one agent's findings but not the other
        String notes
) {}