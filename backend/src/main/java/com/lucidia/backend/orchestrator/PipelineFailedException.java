package com.lucidia.backend.orchestrator;

public class PipelineFailedException extends RuntimeException {
    public PipelineFailedException(String message) {
        super(message);
    }
}