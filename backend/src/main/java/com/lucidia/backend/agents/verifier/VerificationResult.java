package com.lucidia.backend.agents.verifier;

import java.util.List;

public record VerificationResult(
        boolean available,
        boolean verified,
        List<String> flags,
        String notes
) {
    public static VerificationResult unavailable(String reason) {
        return new VerificationResult(false, false, List.of(), reason);
    }

    public static VerificationResult of(boolean verified, List<String> flags, String notes) {
        return new VerificationResult(true, verified, flags, notes);
    }
}