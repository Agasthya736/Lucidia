package com.lucidia.backend.synthesis;

import com.lucidia.backend.triage.AggregatedFindings;

public interface ReportSynthesisProvider {
    String getProviderId();
    GroundedReport synthesize(AggregatedFindings findings, String customApiKey);
    boolean isAvailable();
}
