package com.lucidia.backend.agents.verifier;

import java.util.List;

public record VerificationResult(
        boolean verified,          // true if no unsupported claims found
        List<String> flags,        // specific claims not traceable to either vision agent's observations
        String notes
) {}