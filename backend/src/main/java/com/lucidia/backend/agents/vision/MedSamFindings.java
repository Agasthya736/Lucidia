package com.lucidia.backend.agents.vision;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MedSamFindings(
        @JsonProperty("lesion_found") boolean lesionFound,
        @JsonProperty("area_px") Integer areaPx,
        @JsonProperty("area_mm2") Double areaMm2,
        List<Integer> bbox,
        List<Double> centroid,
        @JsonProperty("hu_mean") Double huMean,
        @JsonProperty("hu_min") Double huMin,
        @JsonProperty("hu_max") Double huMax,
        Double confidence
) {
    public String toPromptContext() {
        if (!lesionFound) {
            return "MedSAM segmentation: no distinct region found in the provided box.";
        }
        return """
            MedSAM segmentation findings (quantitative, from automated segmentation - \
            treat as supporting data, not a diagnosis):
            - Segmented area: %.1f mm^2 (%d px)
            - Bounding box: %s
            - Centroid: %s
            - Intensity stats: mean=%.1f, min=%.1f, max=%.1f
            - Model confidence: %.2f
            """.formatted(areaMm2, areaPx, bbox, centroid, huMean, huMin, huMax, confidence);
    }
}