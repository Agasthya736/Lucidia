package com.lucidia.backend.agents.vision;

import java.util.ArrayList;
import java.util.List;

class VisionFindingsParser {

    static VisionFindings parse(String rawText, String provider) {
        String summary = extractField(rawText, "SUMMARY:", "OBSERVATIONS:");
        String observationsBlock = extractField(rawText, "OBSERVATIONS:", "REGION:");
        String region = extractField(rawText, "REGION:", "BBOX:");
        String bboxStr = extractField(rawText, "BBOX:", "CONFIDENCE:");
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
            confidence = 0.5;
        }

        int[] boundingBox = parseBbox(bboxStr);

        return new VisionFindings(provider, summary.trim(), observations, region.trim(), confidence, boundingBox);
    }

    private static int[] parseBbox(String bboxStr) {
        if (bboxStr == null || bboxStr.isBlank()) return null;
        String cleaned = bboxStr.trim();
        if (cleaned.equalsIgnoreCase("unknown") || cleaned.equalsIgnoreCase("none")) return null;

        try {
            String[] parts = cleaned.split(",");
            if (parts.length != 4) return null;
            int[] box = new int[4];
            for (int i = 0; i < 4; i++) {
                box[i] = Integer.parseInt(parts[i].trim());
            }
            // basic sanity check: x2 > x1, y2 > y1
            if (box[2] <= box[0] || box[3] <= box[1]) return null;
            return box;
        } catch (Exception e) {
            return null;
        }
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