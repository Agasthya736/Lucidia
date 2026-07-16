package com.verirad.backend.orchestrator;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Drives a scan submission through the multi-agent pipeline.
 *
 * This is intentionally a plain supervisor class for now rather than
 * a graph/DAG framework - simple enough to reason about and to swap
 * out for Spring AI's agent orchestration primitives (SequentialAgent /
 * ParallelAgent) once the individual agents below are implemented.
 *
 * Wiring TODO:
 *   - VisionAgentService (agents.vision) x2, run in parallel
 *   - ArbiterAgentService (agents.arbiter)
 *   - ExplainabilityAgentService (agents.explainability)
 *   - WritingAgentService (agents.writing)
 *   - VerifierAgentService (not yet scaffolded - add under agents.verifier)
 */
@Service
public class PipelineOrchestrator {

    @Async
    public CompletableFuture<PipelineState> run(PipelineState state) {
        // Stage 1: Vision - two independent agents read the same CT scan
        state.setStage(PipelineState.Stage.VISION);
        // TODO: visionAgentA.analyze(state), visionAgentB.analyze(state) in parallel

        // Stage 2: Arbitration - compare the two reads, flag disagreement
        state.setStage(PipelineState.Stage.ARBITRATION);
        // TODO: arbiterAgent.reconcile(state)

        // Stage 3: Explainability - generate saliency heatmap for the agreed finding
        state.setStage(PipelineState.Stage.EXPLAINABILITY);
        // TODO: explainabilityAgent.annotate(state)

        // Stage 4: Writing - draft structured report sections
        state.setStage(PipelineState.Stage.WRITING);
        // TODO: writingAgent.draft(state)

        // Stage 5: Verification - cross-check claims against findings, flag hallucinations
        state.setStage(PipelineState.Stage.VERIFIED);
        // TODO: verifierAgent.check(state)

        // Human clinician sign-off happens client-side, not here.
        // A report only moves to FINALIZED once the clinician approves it via the API.

        return CompletableFuture.completedFuture(state);
    }
}
