package com.lucidia.backend.synthesis;

/**
 * Neutral, curated potential condition for educational/informational display.
 * Never presented as a diagnosis.
 */
public record PossibleCondition(
        String name,
        String description
) {}
