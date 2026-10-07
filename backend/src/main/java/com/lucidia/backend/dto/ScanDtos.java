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
            String modality,
            boolean isEscalated,
            Instant createdAt,
            String resultBand,
            String bandMessage
    ) {
        public ScanSummary(
                UUID id,
                String status,
                String imageFilename,
                int sliceCount,
                String modality,
                boolean isEscalated,
                Instant createdAt
        ) {
            this(id, status, imageFilename, sliceCount, modality, isEscalated, createdAt, null, null);
        }

        public static ScanSummary from(Scan scan) {
            String band = null;
            String bandMsg = null;
            if (scan.getTriageJson() != null && !scan.getTriageJson().isBlank()) {
                try {
                    com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(scan.getTriageJson());
                    band = com.lucidia.backend.triage.AggregatedFindings.mapToResultBand(n.path("overallStatus").asText(null));
                    bandMsg = com.lucidia.backend.triage.AggregatedFindings.getBandMessage(band);
                } catch (Exception ignored) {}
            }
            return new ScanSummary(
                    scan.getId(),
                    scan.getStatus().name(),
                    scan.getImageFilename(),
                    scan.getSliceCount(),
                    scan.getModality(),
                    scan.isEscalated(),
                    scan.getCreatedAt(),
                    band,
                    bandMsg
            );
        }
    }

    public record ScanDetail(
            UUID id,
            String status,
            String imageFilename,
            int sliceCount,
            Object sliceFilenames,
            String modality,
            String clinicalNotes,
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
            Instant finalizedAt,
            String resultBand,
            String bandMessage
    ) {
        public ScanDetail(
                UUID id,
                String status,
                String imageFilename,
                int sliceCount,
                Object sliceFilenames,
                String modality,
                String clinicalNotes,
                boolean isEscalated,
                Object triage,
                Object report,
                Object verification,
                String reviewerName,
                String reviewerCredentials,
                String signOffNotes,
                Object visionA,
                Object visionB,
                Object arbitration,
                Object medSam,
                String errorMessage,
                Instant createdAt,
                Instant completedAt,
                Instant finalizedAt
        ) {
            this(
                    id, status, imageFilename, sliceCount, sliceFilenames, modality, clinicalNotes,
                    isEscalated, triage, report, verification, reviewerName, reviewerCredentials,
                    signOffNotes, visionA, visionB, arbitration, medSam, errorMessage,
                    createdAt, completedAt, finalizedAt, null, null
            );
        }
    }

    public record FinalizeRequest(
            String reviewerName,
            String reviewerCredentials,
            String notes
    ) {}
}