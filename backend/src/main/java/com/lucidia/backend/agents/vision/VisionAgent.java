package com.lucidia.backend.agents.vision;

/**
 * Implemented once per model provider. The orchestrator calls two
 * different implementations on the same scan and passes both
 * VisionFindings to the Arbiter for consensus checking.
 */
public interface VisionAgent {
    VisionFindings analyze(byte[] imageBytes, String mimeType);
    String providerName();
}