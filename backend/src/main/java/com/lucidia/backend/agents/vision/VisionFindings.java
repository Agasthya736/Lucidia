package com.lucidia.backend.agents.vision;

import java.util.List;

public record VisionFindings(
        String provider,
        String summary,
        List<String> observations,
        String regionDescription,
        double confidence,
        int[] boundingBox // [x1, y1, x2, y2] in pixel coords relative to the image, or null if unavailable
) {}