package com.lucidia.backend.agents.verifier;

import java.util.List;

public record VerificationResult(
        boolean available,
        boolean verified,
        List<String> flags,
        String notes,
        double groundingScore
) {
    public static VerificationResult unavailable(String reason) {
        return new VerificationResult(false, false, List.of(), reason, 0.0);
    }

    public static VerificationResult of(boolean verified, List<String> flags, String notes, double groundingScore) {
        return new VerificationResult(true, verified, flags, notes, groundingScore);
    }

    public static VerificationResult of(boolean verified, List<String> flags, String notes) {
        return new VerificationResult(true, verified, flags, notes, verified ? 1.0 : 0.5);
    }
}