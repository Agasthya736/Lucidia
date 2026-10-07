package com.lucidia.backend.triage;

import java.util.List;
import java.util.Map;

public record SliceFindings(
        int sliceIndex,
        String sliceFilename,
        boolean hasAbnormality,
        String classification, // NO_FINDINGS_DETECTED, FINDINGS_DETECTED, INCONCLUSIVE
        double confidence,
        List<DetectedLesion> lesions,
        Map<String, Object> metrics
) {
    public static SliceFindings normal(int sliceIndex, String filename, double confidence) {
        return new SliceFindings(
                sliceIndex,
                filename,
                false,
                "NO_FINDINGS_DETECTED",
                confidence,
                List.of(),
                Map.of("lesionCount", 0)
        );
    }
}
