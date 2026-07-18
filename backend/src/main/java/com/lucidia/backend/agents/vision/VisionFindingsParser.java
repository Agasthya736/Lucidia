package com.lucidia.backend.agents.vision;

import java.util.ArrayList;
import java.util.List;

class VisionFindingsParser {

    static VisionFindings parse(String rawText, String provider) {
        String summary = extractField(rawText, "SUMMARY:", "OBSERVATIONS:");
        String observationsBlock = extractField(rawText, "OBSERVATIONS:", "REGION:");
        String region = extractField(rawText, "REGION:", "CONFIDENCE:");
        String confidenceStr = extractField(rawText, "CONFIDENCE:", null);

        List<String> observations = new ArrayList<>();
        for (String line : observationsBlock.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-")) {
                observations.add(trimmed.substring(1).trim());
            }
        }

        double confidence;
        try {
            confidence = Double.parseDouble(confidenceStr.trim());
        } catch (Exception e) {
            confidence = 0.5; // fallback if the model didn't follow the format exactly
        }

        return new VisionFindings(provider, summary.trim(), observations, region.trim(), confidence);
    }

    private static String extractField(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start == -1) return "";
        start += startMarker.length();
        int end = (endMarker != null) ? text.indexOf(endMarker, start) : text.length();
        if (end == -1) end = text.length();
        return text.substring(start, end);
    }
}