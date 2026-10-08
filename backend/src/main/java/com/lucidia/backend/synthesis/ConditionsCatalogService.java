package com.lucidia.backend.synthesis;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
 * Curated catalog mapping observation categories to 2-4 possible conditions.
 * conditions_map.json is marked "REVIEW WITH A MEDICAL SOURCE BEFORE RELEASE".
 * Conditions are returned ONLY when the result band is FINDINGS_DETECTED.
 *
 * The app refuses to start if the catalog cannot be loaded, because a
 * FINDINGS_DETECTED result
 * with no conditions section would be silently incomplete.
 */
@Service
public class ConditionsCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ConditionsCatalogService.class);
    private static final String GENERAL = "GENERAL_OBSERVATION";
    private static final int MAX_CONDITIONS = 4;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<PossibleCondition>> categoryMap = new LinkedHashMap<>();

    /** Whole-word patterns, checked in order. The first match wins. */
    private static final Map<String, Pattern> CATEGORY_PATTERNS = new LinkedHashMap<>();

    static {
        add("RAISED_SKIN_AREA", "\\b(raised|bump|wart|papule)\\b");
        add("REDDENED_AREA", "\\b(red|reddened|erythema|rash)\\b");
        add("DARKER_PATCH", "\\b(dark|darker|pigment\\w*|brown|patch)\\b");
        add("TEXTURE_VARIATION", "\\b(texture|scaly|rough|keratos\\w*)\\b");
        add("NODULE_LIKE_DENSITY", "\\b(nodule|nodules|mass|masses|coin lesion)\\b");
        add("ASYMMETRY_AREA", "\\b(asymmetr\\w*|contour\\w*)\\b");
        add("FOCAL_OPACITY", "\\b(opacity|opacities|infiltrate\\w*|consolidation\\w*)\\b");
        add("TISSUE_DENSITY_VARIATION", "\\b(density|tissue)\\b");
    }

    private static void add(String category, String regex) {
        CATEGORY_PATTERNS.put(category, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    @PostConstruct
    public void init() {
        Map<String, List<PossibleCondition>> loaded = new LinkedHashMap<>();
        try {
            ClassPathResource resource = new ClassPathResource("conditions_map.json");
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                JsonNode categoriesNode = root.path("categories");
                if (!categoriesNode.isObject()) {
                    throw new IllegalStateException("conditions_map.json has no 'categories' object");
                }
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
                    loaded.put(key, Collections.unmodifiableList(conditions));
                });
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not load conditions_map.json: " + e.getMessage(), e);
        }

        List<PossibleCondition> general = loaded.get(GENERAL);
        if (general == null || general.isEmpty()) {
            throw new IllegalStateException("conditions_map.json must contain a non-empty " + GENERAL + " category");
        }
        for (String category : CATEGORY_PATTERNS.keySet()) {
            if (loaded.getOrDefault(category, List.of()).isEmpty()) {
                throw new IllegalStateException("conditions_map.json has no conditions for category " + category);
            }
        }

        categoryMap.clear();
        categoryMap.putAll(loaded);
        log.info("Loaded {} curated condition categories from conditions_map.json", categoryMap.size());
    }

    /**
     * Returns up to 4 curated conditions, only when the result band is
     * FINDINGS_DETECTED.
     * Returns an empty list for every other band. The order does not mean one is
     * more likely;
     * the report text must say so.
     */
    public List<PossibleCondition> getPossibleConditionsForFindings(AggregatedFindings findings) {
        if (findings == null) {
            return List.of();
        }
        if (!"FINDINGS_DETECTED".equalsIgnoreCase(findings.resultBand())) {
            return List.of();
        }

        List<PossibleCondition> result = new ArrayList<>();
        List<DetectedLesion> lesions = findings.topLesions();

        if (lesions != null) {
            for (DetectedLesion lesion : lesions) {
                List<PossibleCondition> fromCat = categoryMap.get(matchCategory(lesion.lesionType()));
                if (fromCat == null)
                    continue;
                for (PossibleCondition pc : fromCat) {
                    if (result.stream().noneMatch(e -> e.name().equalsIgnoreCase(pc.name()))) {
                        result.add(pc);
                        if (result.size() >= MAX_CONDITIONS)
                            break;
                    }
                }
                if (result.size() >= MAX_CONDITIONS)
                    break;
            }
        }

        if (result.isEmpty()) {
            result.addAll(categoryMap.get(GENERAL));
        }

        return List.copyOf(result);
    }

    /**
     * Package-visible so tests can check every category the detectors can produce.
     */
    String matchCategory(String observation) {
        if (observation == null || observation.isBlank())
            return GENERAL;
        for (Map.Entry<String, Pattern> entry : CATEGORY_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(observation).find()) {
                return entry.getKey();
            }
        }
        return GENERAL;
    }
}