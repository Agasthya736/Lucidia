package com.lucidia.backend.synthesis;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.lucidia.backend.triage.AggregatedFindings;

@Service
public class ReportSynthesisService {

    private static final Logger log = LoggerFactory.getLogger(ReportSynthesisService.class);

    private final List<ReportSynthesisProvider> providers;
    private final String preferredProviderId;

    public ReportSynthesisService(
            List<ReportSynthesisProvider> providers,
            @Value("${lucidia.synthesis.provider:gemini}") String preferredProviderId) {
        this.providers = providers;
        this.preferredProviderId = preferredProviderId;
    }

    public GroundedReport synthesizeReport(AggregatedFindings findings, String customApiKey) {
        // If high-confidence clean, we do not call LLM providers - return instant auto-summary
        if (findings.isHighConfidenceClean(0.85)) {
            log.info("Scan is clean with high confidence ({}). Generating auto-summary, skipping LLM.",
                    findings.overallConfidence());
            return GroundedReport.createCleanAutoSummary(findings);
        }

        // Find preferred provider
        ReportSynthesisProvider selected = providers.stream()
                .filter(p -> p.getProviderId().equalsIgnoreCase(preferredProviderId))
                .findFirst()
                .orElse(null);

        if (selected != null && (selected.isAvailable() || (customApiKey != null && !customApiKey.isBlank()))) {
            try {
                return selected.synthesize(findings, customApiKey);
            } catch (Exception e) {
                log.warn("Preferred provider {} failed: {}. Falling back to deterministic synthesis.",
                        preferredProviderId, e.getMessage());
            }
        }

        // Fallback provider
        return FallbackReportSynthesisProvider.createGroundedFromDetector(findings);
    }
}
