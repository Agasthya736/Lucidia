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
        String overallStatus, // FINDINGS_DETECTED, INCONCLUSIVE, NO_FINDINGS_DETECTED
        double overallConfidence,
        List<SliceFindings> sliceFindings,
        List<DetectedLesion> topLesions,
        Map<String, List<DetectedLesion>> findingsByRegion,
        String summaryEvidence,
        String bandMessage
) {
    public static final String BAND_FINDINGS_DETECTED = "FINDINGS_DETECTED";
    public static final String BAND_INCONCLUSIVE = "INCONCLUSIVE";
    public static final String BAND_NO_FINDINGS_DETECTED = "NO_FINDINGS_DETECTED";

    public static final String MSG_FINDINGS_DETECTED = "The tool detected features that may need attention. See a doctor.";
    public static final String MSG_INCONCLUSIVE = "Could not assess this image. Please see a doctor if you are concerned.";
    public static final String MSG_NO_FINDINGS_DETECTED = "The tool did not detect the features it looks for. This is NOT a clearance. If you have symptoms, see a doctor.";

    public AggregatedFindings(
            int totalSlices,
            int abnormalSlicesCount,
            String overallStatus,
            double overallConfidence,
            List<SliceFindings> sliceFindings,
            List<DetectedLesion> topLesions,
            Map<String, List<DetectedLesion>> findingsByRegion,
            String summaryEvidence) {
        this(
                totalSlices,
                abnormalSlicesCount,
                mapToResultBand(overallStatus),
                overallConfidence,
                sliceFindings,
                topLesions,
                findingsByRegion,
                summaryEvidence,
                getBandMessage(mapToResultBand(overallStatus))
        );
    }

    public static String mapToResultBand(String status) {
        if (status == null || status.isBlank()) {
            return BAND_INCONCLUSIVE;
        }
        return switch (status.toUpperCase().trim()) {
            case "ABNORMAL", BAND_FINDINGS_DETECTED -> BAND_FINDINGS_DETECTED;
            case "NORMAL", BAND_NO_FINDINGS_DETECTED -> BAND_NO_FINDINGS_DETECTED;
            case "LOW_CONFIDENCE", "EQUIVOCAL", BAND_INCONCLUSIVE -> BAND_INCONCLUSIVE;
            default -> BAND_INCONCLUSIVE;
        };
    }

    public static String getBandMessage(String band) {
        return switch (mapToResultBand(band)) {
            case BAND_FINDINGS_DETECTED -> MSG_FINDINGS_DETECTED;
            case BAND_INCONCLUSIVE -> MSG_INCONCLUSIVE;
            case BAND_NO_FINDINGS_DETECTED -> MSG_NO_FINDINGS_DETECTED;
            default -> MSG_INCONCLUSIVE;
        };
    }

    public String resultBand() {
        return mapToResultBand(overallStatus);
    }

    public boolean isHighConfidenceClean(double confidenceThreshold) {
        return BAND_NO_FINDINGS_DETECTED.equalsIgnoreCase(mapToResultBand(overallStatus))
                && overallConfidence >= confidenceThreshold;
    }

    public static AggregatedFindings fromSliceFindings(List<SliceFindings> slices, double lowConfidenceThreshold) {
        if (slices == null || slices.isEmpty()) {
            return new AggregatedFindings(
                    0, 0, BAND_INCONCLUSIVE, 0.0, List.of(), List.of(), Map.of(),
                    "Could not assess this image.",
                    MSG_INCONCLUSIVE
            );
        }

        int abnormalCount = 0;
        double sumConfidence = 0.0;
        List<DetectedLesion> allLesions = new ArrayList<>();
        Map<String, List<DetectedLesion>> regionMap = new HashMap<>();

        for (SliceFindings sf : slices) {
            sumConfidence += sf.confidence();
            if (sf.hasAbnormality()
                    || "ABNORMAL".equalsIgnoreCase(sf.classification())
                    || BAND_FINDINGS_DETECTED.equalsIgnoreCase(sf.classification())) {
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
            overallStatus = BAND_FINDINGS_DETECTED;
        } else if (avgConfidence < lowConfidenceThreshold) {
            overallStatus = BAND_INCONCLUSIVE;
        } else {
            overallStatus = BAND_NO_FINDINGS_DETECTED;
        }

        StringBuilder evidence = new StringBuilder();
        evidence.append("Series Analysis: ").append(slices.size()).append(" slices analyzed. ");
        evidence.append("Slices with findings: ").append(abnormalCount).append(". ");
        evidence.append("Result Band: ").append(overallStatus).append(" (Confidence: ").append(avgConfidence).append("). ");
        if (!allLesions.isEmpty()) {
            evidence.append("Observed ").append(allLesions.size()).append(" focal feature(s): ");
            for (DetectedLesion l : top) {
                evidence.append("[").append(l.anatomicalRegion()).append(": ")
                        .append(l.lesionType()).append(", conf=").append(l.confidence())
                        .append(", size=").append(l.sizeMm()).append("mm] ");
            }
        } else {
            evidence.append("The tool did not detect the features it looks for across the series.");
        }

        return new AggregatedFindings(
                slices.size(),
                abnormalCount,
                overallStatus,
                avgConfidence,
                Collections.unmodifiableList(slices),
                Collections.unmodifiableList(top),
                Collections.unmodifiableMap(regionMap),
                evidence.toString().trim(),
                getBandMessage(overallStatus)
        );
    }
}
