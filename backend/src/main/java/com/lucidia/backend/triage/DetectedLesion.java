package com.lucidia.backend.triage;

import java.util.List;

public record DetectedLesion(
        String lesionType,
        String anatomicalRegion,
        List<Integer> boundingBox, // [x1, y1, x2, y2] on 0-1000 scale
        double confidence,
        double densityHu,
        double sizeMm,
        String description
) {}
