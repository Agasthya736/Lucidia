package com.lucidia.backend.synthesis;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucidia.backend.triage.AggregatedFindings;
import com.lucidia.backend.triage.DetectedLesion;

import jakarta.annotation.PostConstruct;

/**
 * Curated catalog mapping visual/radiological observations to 2-4 possible neutral conditions.
 * Every entry is marked "REVIEW WITH A MEDICAL SOURCE BEFORE RELEASE".
 * Results are ONLY returned when the result band is FINDINGS_DETECTED.
 */
@Service
public class ConditionsCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ConditionsCatalogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<PossibleCondition>> categoryMap = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("conditions_map.json");
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                JsonNode categoriesNode = root.path("categories");
                if (categoriesNode.isObject()) {
                    categoriesNode.fields().forEachRemaining(entry -> {
                        String key = entry.getKey().toUpperCase();
                        JsonNode condArray = entry.getValue().path("possibleConditions");
                        List<PossibleCondition> conditions = new ArrayList<>();
                        if (condArray.isArray()) {
                            for (JsonNode cNode : condArray) {
                                String name = cNode.path("name").asText("").trim();
                                String desc = cNode.path("description").asText("").trim();
                                if (!name.isEmpty()) {
                                    conditions.add(new PossibleCondition(name, desc));
                                }
                            }
                        }
                        categoryMap.put(key, Collections.unmodifiableList(conditions));
                    });
                }
            }
            log.info("Loaded {} curated condition observation categories from conditions_map.json", categoryMap.size());
        } catch (Exception e) {
            log.error("Failed to load conditions_map.json: {}", e.getMessage(), e);
        }
    }

    /**
     * Resolves 2-4 curated possible conditions strictly when result band is FINDINGS_DETECTED.
     * Returns empty list for INCONCLUSIVE, NO_FINDINGS_DETECTED, or empty findings.
     */
    public List<PossibleCondition> getPossibleConditionsForFindings(AggregatedFindings findings) {
        if (findings == null) {
            return List.of();
        }

        // Rule: The UI/report shows possible conditions ONLY when result band is FINDINGS_DETECTED
        if (!"FINDINGS_DETECTED".equalsIgnoreCase(findings.resultBand())) {
            return List.of();
        }

        List<PossibleCondition> result = new ArrayList<>();
        List<DetectedLesion> lesions = findings.topLesions();

        if (lesions != null && !lesions.isEmpty()) {
            for (DetectedLesion lesion : lesions) {
                String type = lesion.lesionType();
                String matchedCategory = matchCategory(type);
                List<PossibleCondition> fromCat = categoryMap.get(matchedCategory);
                if (fromCat != null) {
                    for (PossibleCondition pc : fromCat) {
                        if (result.stream().noneMatch(existing -> existing.name().equalsIgnoreCase(pc.name()))) {
                            result.add(pc);
                            if (result.size() >= 4) break;
                        }
                    }
                }
                if (result.size() >= 4) break;
            }
        }

        // Fallback to general observation if specific lesion didn't match
        if (result.isEmpty()) {
            List<PossibleCondition> general = categoryMap.get("GENERAL_OBSERVATION");
            if (general != null) {
                result.addAll(general);
            }
        }

        return List.copyOf(result);
    }

    private String matchCategory(String observation) {
        if (observation == null) return "GENERAL_OBSERVATION";
        String lower = observation.toLowerCase();

        if (lower.contains("raised") || lower.contains("bump") || lower.contains("wart") || lower.contains("papule")) {
            return "RAISED_SKIN_AREA";
        }
        if (lower.contains("red") || lower.contains("erythema") || lower.contains("rash")) {
            return "REDDENED_AREA";
        }
        if (lower.contains("dark") || lower.contains("pigment") || lower.contains("brown") || lower.contains("patch")) {
            return "DARKER_PATCH";
        }
        if (lower.contains("texture") || lower.contains("scaly") || lower.contains("rough") || lower.contains("keratos")) {
            return "TEXTURE_VARIATION";
        }
        if (lower.contains("nodule") || lower.contains("mass") || lower.contains("coin lesion")) {
            return "NODULE_LIKE_DENSITY";
        }
        if (lower.contains("asymmetry") || lower.contains("contour")) {
            return "ASYMMETRY_AREA";
        }
        if (lower.contains("opacity") || lower.contains("infiltrate") || lower.contains("consolidation")) {
            return "FOCAL_OPACITY";
        }
        if (lower.contains("density") || lower.contains("tissue")) {
            return "TISSUE_DENSITY_VARIATION";
        }

        return "GENERAL_OBSERVATION";
    }
}
