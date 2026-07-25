package com.lucidia.backend.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.lucidia.backend.agents.AgentOutcome;
import com.lucidia.backend.agents.arbiter.ArbiterAgent;
import com.lucidia.backend.agents.arbiter.ArbitrationResult;
import com.lucidia.backend.agents.verifier.VerificationResult;
import com.lucidia.backend.agents.verifier.VerifierAgent;
import com.lucidia.backend.agents.vision.GeminiVisionAgent;
import com.lucidia.backend.agents.vision.OllamaVisionAgent;
import com.lucidia.backend.agents.vision.VisionFindings;
import com.lucidia.backend.agents.writing.FallbackReportBuilder;
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
        List<String> warnings = new ArrayList<>();

        // Run both vision agents concurrently instead of sequentially -
        // they're independent reads of the same image, no reason to wait.
        CompletableFuture<AgentOutcome<VisionFindings>> futureA = CompletableFuture.supplyAsync(
                () -> safeCall(() -> geminiVisionAgent.analyze(imageBytes, mimeType), "Gemini vision agent"));
        CompletableFuture<AgentOutcome<VisionFindings>> futureB = CompletableFuture.supplyAsync(
                () -> safeCall(() -> ollamaVisionAgent.analyze(imageBytes, mimeType), "Ollama vision agent"));

        CompletableFuture.allOf(futureA, futureB).join();

        AgentOutcome<VisionFindings> outcomeA = futureA.join();
        AgentOutcome<VisionFindings> outcomeB = futureB.join();

        if (!outcomeA.success() && !outcomeB.success()) {
            throw new PipelineFailedException(
                    "Both vision agents failed after exhausting retries - Gemini: " + outcomeA.errorMessage()
                            + " | Ollama: " + outcomeB.errorMessage());
        }

        VisionFindings visionA = outcomeA.success() ? outcomeA.value() : null;
        VisionFindings visionB = outcomeB.success() ? outcomeB.value() : null;
        boolean degraded = visionA == null || visionB == null;

        if (!outcomeA.success()) warnings.add("Gemini vision agent failed after retries: " + outcomeA.errorMessage());
        if (!outcomeB.success()) warnings.add("Ollama vision agent failed after retries: " + outcomeB.errorMessage());

        ArbitrationResult arbitration;
        if (degraded) {
            arbitration = ArbitrationResult.unavailable(
                    "Consensus check skipped - only one vision reading was available.");
        } else {
            try {
                arbitration = arbiterAgent.reconcile(visionA, visionB);
            } catch (Exception e) {
                arbitration = ArbitrationResult.unavailable("Arbiter failed: " + e.getMessage());
                warnings.add("Arbiter failed: " + e.getMessage());
            }
        }

        ReportDraft report;
        try {
            report = writingAgent.draft(visionA, visionB, arbitration);
        } catch (Exception e) {
            warnings.add("Writing agent failed after retries, using fallback template: " + e.getMessage());
            report = FallbackReportBuilder.build(visionA, visionB);
        }

        VerificationResult verification;
        try {
            verification = verifierAgent.verify(report, visionA, visionB);
        } catch (Exception e) {
            verification = VerificationResult.unavailable("Verifier failed: " + e.getMessage());
            warnings.add("Verifier failed: " + e.getMessage());
        }

        return new PipelineResult(visionA, visionB, arbitration, report, verification, degraded, warnings);
    }

    private <T> AgentOutcome<T> safeCall(Supplier<T> call, String agentName) {
        try {
            return AgentOutcome.ok(call.get());
        } catch (Exception e) {
            return AgentOutcome.failed(agentName + ": " + e.getMessage());
        }
    }
}