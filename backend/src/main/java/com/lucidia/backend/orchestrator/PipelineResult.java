package com.lucidia.backend.orchestrator;

import com.lucidia.backend.agents.arbiter.ArbitrationResult;
import com.lucidia.backend.agents.vision.VisionFindings;

public record PipelineResult(
        VisionFindings visionA,
        VisionFindings visionB,
        ArbitrationResult arbitration
) {}