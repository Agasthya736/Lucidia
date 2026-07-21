package com.lucidia.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.lucidia.backend.scan.Scan;

public class ScanDtos {

    public record ScanSummary(UUID id, String status, String imageFilename, Instant createdAt) {
        public static ScanSummary from(Scan scan) {
            return new ScanSummary(scan.getId(), scan.getStatus().name(), scan.getImageFilename(), scan.getCreatedAt());
        }
    }

    public record ScanDetail(
            UUID id, String status, String imageFilename,
            Object visionA, Object visionB, Object arbitration, Object report, Object verification,
            String errorMessage, Instant createdAt, Instant completedAt, Instant finalizedAt
    ) {}
}