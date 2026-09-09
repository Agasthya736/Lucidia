package com.lucidia.backend.triage;

public record SliceInput(
        int sliceIndex,
        String filename,
        byte[] bytes,
        String mimeType
) {}
