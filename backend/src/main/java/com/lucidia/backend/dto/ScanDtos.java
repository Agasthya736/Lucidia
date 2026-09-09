package com.lucidia.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.lucidia.backend.scan.Scan;

public class ScanDtos {

    public record ScanSummary(
            UUID id,
            String status,
            String imageFilename,
            int sliceCount,
            boolean isEscalated,
            Instant createdAt
    ) {
        public static ScanSummary from(Scan scan) {
            return new ScanSummary(
                    scan.getId(),
                    scan.getStatus().name(),
                    scan.getImageFilename(),
                    scan.getSliceCount(),
                    scan.isEscalated(),
                    scan.getCreatedAt()
            );
        }
    }

    public record ScanDetail(
            UUID id,
            String status,
            String imageFilename,
            int sliceCount,
            Object sliceFilenames,
            boolean isEscalated,
            Object triage,
            Object report,
            Object verification,
            String reviewerName,
            String reviewerCredentials,
            String signOffNotes,
            // Legacy aliases for backward compatibility if needed
            Object visionA,
            Object visionB,
            Object arbitration,
            Object medSam,
            String errorMessage,
            Instant createdAt,
            Instant completedAt,
            Instant finalizedAt
    ) {}

    public record FinalizeRequest(
            String reviewerName,
            String reviewerCredentials,
            String notes
    ) {}
}