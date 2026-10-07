package com.lucidia.backend.synthesis;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lucidia.backend.triage.AggregatedFindings;
import com.lucidia.backend.triage.DetectedLesion;

class ConditionsCatalogServiceTest {

    private ConditionsCatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new ConditionsCatalogService();
        catalogService.init();
    }

    @Test
    void testNoConditionsReturnedForNoFindingsDetected() {
        AggregatedFindings clean = new AggregatedFindings(
                5, 0, "NO_FINDINGS_DETECTED", 0.95, List.of(), List.of(), Map.of(), "Clean series"
        );

        List<PossibleCondition> conditions = catalogService.getPossibleConditionsForFindings(clean);
        assertNotNull(conditions);
        assertTrue(conditions.isEmpty(), "Possible conditions must NEVER be returned for NO_FINDINGS_DETECTED");
    }

    @Test
    void testNoConditionsReturnedForInconclusive() {
        AggregatedFindings inconclusive = new AggregatedFindings(
                1, 0, "INCONCLUSIVE", 0.55, List.of(), List.of(), Map.of(), "Inconclusive scan"
        );

        List<PossibleCondition> conditions = catalogService.getPossibleConditionsForFindings(inconclusive);
        assertNotNull(conditions);
        assertTrue(conditions.isEmpty(), "Possible conditions must NEVER be returned for INCONCLUSIVE");
    }

    @Test
    void testCuratedConditionsReturnedForFindingsDetected() {
        DetectedLesion lesion = new DetectedLesion(
                "a raised skin area", "External Cutaneous", List.of(10, 10, 50, 50),
                0.90, 20.0, 6.0, "Elevated warty surface feature"
        );

        AggregatedFindings findings = new AggregatedFindings(
                1, 1, "FINDINGS_DETECTED", 0.90, List.of(), List.of(lesion),
                Map.of("External Cutaneous", List.of(lesion)), "Finding detected"
        );

        List<PossibleCondition> conditions = catalogService.getPossibleConditionsForFindings(findings);
        assertNotNull(conditions);
        assertFalse(conditions.isEmpty(), "Should return curated conditions for FINDINGS_DETECTED");
        assertTrue(conditions.size() >= 2 && conditions.size() <= 4, "Should return between 2 and 4 possible conditions");

        for (PossibleCondition pc : conditions) {
            assertNotNull(pc.name());
            assertFalse(pc.name().isBlank());
            assertNotNull(pc.description());
            assertFalse(pc.description().isBlank());
            assertFalse(pc.description().toLowerCase().contains("you have"), "Must never say 'you have X'");
        }
    }
}
