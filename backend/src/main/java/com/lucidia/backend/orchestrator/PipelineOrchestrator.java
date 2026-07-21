package com.lucidia.backend.orchestrator;

import org.springframework.stereotype.Service;

import com.lucidia.backend.agents.arbiter.ArbiterAgent;
import com.lucidia.backend.agents.arbiter.ArbitrationResult;
import com.lucidia.backend.agents.verifier.VerificationResult;
import com.lucidia.backend.agents.verifier.VerifierAgent;
import com.lucidia.backend.agents.vision.GeminiVisionAgent;
import com.lucidia.backend.agents.vision.OllamaVisionAgent;
import com.lucidia.backend.agents.vision.VisionFindings;
import com.lucidia.backend.agents.writing.ReportDraft;
import com.lucidia.backend.agents.writing.WritingAgent;

@Service
public class PipelineOrchestrator {

    private final GeminiVisionAgent geminiVisionAgent;
    private final OllamaVisionAgent ollamaVisionAgent;
    private final ArbiterAgent arbiterAgent;
    private final WritingAgent writingAgent;
    private final VerifierAgent verifierAgent;

    public PipelineOrchestrator(
            GeminiVisionAgent geminiVisionAgent,
            OllamaVisionAgent ollamaVisionAgent,
            ArbiterAgent arbiterAgent,
            WritingAgent writingAgent,
            VerifierAgent verifierAgent) {
        this.geminiVisionAgent = geminiVisionAgent;
        this.ollamaVisionAgent = ollamaVisionAgent;
        this.arbiterAgent = arbiterAgent;
        this.writingAgent = writingAgent;
        this.verifierAgent = verifierAgent;
    }

    public PipelineResult run(byte[] imageBytes, String mimeType) {
        VisionFindings visionA = geminiVisionAgent.analyze(imageBytes, mimeType);
        VisionFindings visionB = ollamaVisionAgent.analyze(imageBytes, mimeType);

        ArbitrationResult arbitration = arbiterAgent.reconcile(visionA, visionB);

        ReportDraft report = writingAgent.draft(visionA, visionB, arbitration);

        VerificationResult verification = verifierAgent.verify(report, visionA, visionB);

        return new PipelineResult(visionA, visionB, arbitration, report, verification);
    }
}