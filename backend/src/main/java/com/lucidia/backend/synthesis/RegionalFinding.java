package com.lucidia.backend.synthesis;

import java.util.List;

public record RegionalFinding(
        String region,
        String status, // NORMAL, ABNORMAL, EQUIVOCAL
        String description,
        List<Integer> sliceIndices
) {}
