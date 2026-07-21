package com.lucidia.backend.orchestrator;

import com.lucidia.backend.agents.arbiter.ArbiterAgent;
import com.lucidia.backend.agents.arbiter.ArbitrationResult;
import com.lucidia.backend.agents.vision.GeminiVisionAgent;
import com.lucidia.backend.agents.vision.OllamaVisionAgent;
import com.lucidia.backend.agents.vision.VisionFindings;
import org.springframework.stereotype.Service;

/**
 * Drives a scan submission through the multi-agent pipeline.
 *
 * Current stages: Vision (dual, parallel-capable) -> Arbitration
 * Still to add: Explainability, Writing, Verifier (Mon)
 */
@Service
public class PipelineOrchestrator {

    private final GeminiVisionAgent geminiVisionAgent;
    private final OllamaVisionAgent ollamaVisionAgent;
    private final ArbiterAgent arbiterAgent;

    public PipelineOrchestrator(
            GeminiVisionAgent geminiVisionAgent,
            OllamaVisionAgent ollamaVisionAgent,
            ArbiterAgent arbiterAgent) {
        this.geminiVisionAgent = geminiVisionAgent;
        this.ollamaVisionAgent = ollamaVisionAgent;
        this.arbiterAgent = arbiterAgent;
    }

    public PipelineResult run(byte[] imageBytes, String mimeType) {
        // Stage 1: Vision - two independent agents read the same scan
        VisionFindings visionA = geminiVisionAgent.analyze(imageBytes, mimeType);
        VisionFindings visionB = ollamaVisionAgent.analyze(imageBytes, mimeType);

        // Stage 2: Arbitration - compare the two reads, flag disagreement
        ArbitrationResult arbitration = arbiterAgent.reconcile(visionA, visionB);

        // TODO Mon: Explainability, Writing, Verifier stages

        return new PipelineResult(visionA, visionB, arbitration);
    }
}