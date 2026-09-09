package com.lucidia.backend.triage;

import java.util.List;

public interface TriageDetector {
    /**
     * Analyzes an entire series of CT slices and produces aggregated structured findings.
     */
    AggregatedFindings analyzeSeries(List<SliceInput> slices);

    /**
     * Analyzes a single slice and produces structured slice findings.
     */
    SliceFindings analyzeSlice(SliceInput slice);

    /**
     * Name or identifier of the detector model.
     */
    String getDetectorName();
}
