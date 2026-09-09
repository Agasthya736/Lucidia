package com.lucidia.backend.triage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record AggregatedFindings(
        int totalSlices,
        int abnormalSlicesCount,
        String overallStatus, // NORMAL, ABNORMAL, LOW_CONFIDENCE
        double overallConfidence,
        List<SliceFindings> sliceFindings,
        List<DetectedLesion> topLesions,
        Map<String, List<DetectedLesion>> findingsByRegion,
        String summaryEvidence
) {
    public boolean isHighConfidenceClean(double confidenceThreshold) {
        return "NORMAL".equalsIgnoreCase(overallStatus) && overallConfidence >= confidenceThreshold;
    }

    public static AggregatedFindings fromSliceFindings(List<SliceFindings> slices, double lowConfidenceThreshold) {
        if (slices == null || slices.isEmpty()) {
            return new AggregatedFindings(
                    0, 0, "LOW_CONFIDENCE", 0.0, List.of(), List.of(), Map.of(), "No slices provided"
            );
        }

        int abnormalCount = 0;
        double sumConfidence = 0.0;
        List<DetectedLesion> allLesions = new ArrayList<>();
        Map<String, List<DetectedLesion>> regionMap = new HashMap<>();

        for (SliceFindings sf : slices) {
            sumConfidence += sf.confidence();
            if (sf.hasAbnormality() || "ABNORMAL".equalsIgnoreCase(sf.classification())) {
                abnormalCount++;
            }
            for (DetectedLesion lesion : sf.lesions()) {
                allLesions.add(lesion);
                regionMap.computeIfAbsent(lesion.anatomicalRegion(), k -> new ArrayList<>()).add(lesion);
            }
        }

        double avgConfidence = Math.round((sumConfidence / slices.size()) * 100.0) / 100.0;
        allLesions.sort(Comparator.comparingDouble(DetectedLesion::confidence).reversed());
        List<DetectedLesion> top = allLesions.size() > 5 ? allLesions.subList(0, 5) : allLesions;

        String overallStatus;
        if (abnormalCount > 0) {
            overallStatus = "ABNORMAL";
        } else if (avgConfidence < lowConfidenceThreshold) {
            overallStatus = "LOW_CONFIDENCE";
        } else {
            overallStatus = "NORMAL";
        }

        StringBuilder evidence = new StringBuilder();
        evidence.append("Series Analysis: ").append(slices.size()).append(" slices analyzed. ");
        evidence.append("Abnormal slices: ").append(abnormalCount).append(". ");
        evidence.append("Status: ").append(overallStatus).append(" (Confidence: ").append(avgConfidence).append("). ");
        if (!allLesions.isEmpty()) {
            evidence.append("Detected ").append(allLesions.size()).append(" focal finding(s): ");
            for (DetectedLesion l : top) {
                evidence.append("[").append(l.anatomicalRegion()).append(": ")
                        .append(l.lesionType()).append(", conf=").append(l.confidence())
                        .append(", size=").append(l.sizeMm()).append("mm] ");
            }
        } else {
            evidence.append("No focal abnormalities or suspicious attenuation detected across the series.");
        }

        return new AggregatedFindings(
                slices.size(),
                abnormalCount,
                overallStatus,
                avgConfidence,
                Collections.unmodifiableList(slices),
                Collections.unmodifiableList(top),
                Collections.unmodifiableMap(regionMap),
                evidence.toString().trim()
        );
    }
}
