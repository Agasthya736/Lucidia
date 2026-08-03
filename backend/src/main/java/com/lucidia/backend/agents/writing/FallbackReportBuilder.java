package com.lucidia.backend.agents.writing;

import com.lucidia.backend.agents.vision.VisionFindings;

public class FallbackReportBuilder {

    public static ReportDraft build(VisionFindings a, VisionFindings b) {
        StringBuilder findings = new StringBuilder();
        if (a != null) findings.append("Reading (").append(a.provider()).append("): ").append(a.summary()).append(" ");
        if (b != null) findings.append("Reading (").append(b.provider()).append("): ").append(b.summary());

        String impression = "Automated report generation failed after all retries; findings shown are "
                + "raw, unsynthesized agent output. Clinician review required.";

        String differential = "Not generated - automated report writing failed.";

        return new ReportDraft(findings.toString().trim(), impression, differential, true);
    }
}