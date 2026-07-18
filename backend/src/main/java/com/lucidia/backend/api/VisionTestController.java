package com.lucidia.backend.api;

import com.lucidia.backend.agents.vision.GeminiVisionAgent;
import com.lucidia.backend.agents.vision.OllamaVisionAgent;
import com.lucidia.backend.agents.vision.VisionFindings;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Temporary endpoint for verifying both vision agents work independently
 * before wiring them into the full pipeline. Not part of the real API
 * surface - remove or gate behind an admin role before any real deployment.
 */
@RestController
@RequestMapping("/api/test")
public class VisionTestController {

    private final GeminiVisionAgent geminiVisionAgent;
    private final OllamaVisionAgent ollamaVisionAgent;

    public VisionTestController(GeminiVisionAgent geminiVisionAgent, OllamaVisionAgent ollamaVisionAgent) {
        this.geminiVisionAgent = geminiVisionAgent;
        this.ollamaVisionAgent = ollamaVisionAgent;
    }

    @PostMapping(value = "/vision", consumes = "multipart/form-data")
    public Map<String, VisionFindings> testVision(@RequestParam("image") MultipartFile image) throws IOException {
        byte[] imageBytes = image.getBytes();
        String mimeType = image.getContentType() != null ? image.getContentType() : "image/jpeg";

        VisionFindings geminiResult = geminiVisionAgent.analyze(imageBytes, mimeType);
        VisionFindings ollamaResult = ollamaVisionAgent.analyze(imageBytes, mimeType);

        return Map.of(
                "gemini", geminiResult,
                "ollama", ollamaResult
        );
    }
}