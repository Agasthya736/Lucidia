package com.lucidia.backend.agents.vision;

import java.util.List;

/**
 * Standard output shape for any vision agent, regardless of provider.
 * The Arbiter compares two of these (one per agent) to check consensus.
 */
public record VisionFindings(
        String provider,           // e.g. "gemini-2.5-flash", "qwen2.5vl:7b"
        String summary,            // one-paragraph overall impression
        List<String> observations, // discrete findings, one per notable feature
        String regionDescription,  // rough location of the primary finding, e.g. "upper right lobe"
        double confidence          // 0.0-1.0, self-reported by the model
) {}