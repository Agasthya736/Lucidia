package com.lucidia.backend.api;

import java.io.IOException;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lucidia.backend.orchestrator.PipelineOrchestrator;
import com.lucidia.backend.orchestrator.PipelineResult;

/**
 * Temporary endpoint for verifying the pipeline works end-to-end
 * before building the mobile-facing job submission API. Not part of
 * the real API surface - remove or gate behind an admin role before
 * any real deployment.
 */
@RestController
@RequestMapping("/api/test")
public class VisionTestController {

    private final PipelineOrchestrator orchestrator;

    public VisionTestController(PipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(value = "/vision", consumes = "multipart/form-data")
    public PipelineResult testVision(@RequestParam("image") MultipartFile image) throws IOException {
        byte[] imageBytes = image.getBytes();
        String mimeType = image.getContentType() != null ? image.getContentType() : "image/jpeg";
        return orchestrator.run(imageBytes, mimeType);
    }
}