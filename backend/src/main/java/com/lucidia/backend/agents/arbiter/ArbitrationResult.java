package com.lucidia.backend.agents.arbiter;

import java.util.List;

public record ArbitrationResult(
        boolean available,
        boolean agree,
        double agreementScore,
        List<String> sharedObservations,
        List<String> conflictingObservations,
        String notes
) {
    public static ArbitrationResult unavailable(String reason) {
        return new ArbitrationResult(false, false, 0.0, List.of(), List.of(), reason);
    }

    public static ArbitrationResult of(boolean agree, double score, List<String> shared, List<String> conflicting, String notes) {
        return new ArbitrationResult(true, agree, score, shared, conflicting, notes);
    }
}