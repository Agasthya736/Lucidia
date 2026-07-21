package com.lucidia.backend.agents.arbiter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.lucidia.backend.agents.vision.VisionFindings;

@Service
public class ArbiterAgent {

    private static final double AGREEMENT_THRESHOLD = 0.35;

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "is", "are", "of", "in", "on", "at", "no", "and", "or",
            "with", "within", "without", "to", "for", "this", "slice", "appears",
            "appear", "visible", "noted", "identified", "significant", "obvious"
    );

    public ArbitrationResult reconcile(VisionFindings a, VisionFindings b) {
        Set<String> tokensA = tokenize(a.observations());
        Set<String> tokensB = tokenize(b.observations());

        Set<String> shared = new HashSet<>(tokensA);
        shared.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        double agreementScore = union.isEmpty() ? 0.0 : (double) shared.size() / union.size();
        boolean regionsMatch = regionsRoughlyMatch(a.regionDescription(), b.regionDescription());

        boolean agree = agreementScore >= AGREEMENT_THRESHOLD && regionsMatch;

        String notes = String.format(
                "%s vs %s: token agreement %.2f, regions %s (%s / %s)",
                a.provider(), b.provider(), agreementScore,
                regionsMatch ? "match" : "differ",
                a.regionDescription(), b.regionDescription()
        );

        return new ArbitrationResult(
                agree,
                agreementScore,
                List.of(a.summary(), b.summary()), // shown as context in the UI, not raw tokens
                findMismatchedObservations(a.observations(), b.observations(), tokensA, tokensB),
                notes
        );
    }

    private Set<String> tokenize(List<String> observations) {
        Set<String> tokens = new HashSet<>();
        for (String obs : observations) {
            for (String word : obs.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+")) {
                if (word.length() > 2 && !STOPWORDS.contains(word)) {
                    tokens.add(word);
                }
            }
        }
        return tokens;
    }

    private List<String> findMismatchedObservations(
            List<String> obsA, List<String> obsB, Set<String> tokensA, Set<String> tokensB) {
        List<String> mismatched = new ArrayList<>();
        for (String obs : obsA) {
            if (!hasTokenOverlap(obs, tokensB)) mismatched.add("[A only] " + obs);
        }
        for (String obs : obsB) {
            if (!hasTokenOverlap(obs, tokensA)) mismatched.add("[B only] " + obs);
        }
        return mismatched;
    }

    private boolean hasTokenOverlap(String observation, Set<String> otherTokens) {
        String[] words = observation.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+");
        for (String w : words) {
            if (otherTokens.contains(w)) return true;
        }
        return false;
    }

    private boolean regionsRoughlyMatch(String regionA, String regionB) {
        if (regionA == null || regionB == null) return false;
        String normA = regionA.toLowerCase().trim();
        String normB = regionB.toLowerCase().trim();
        return normA.contains(normB) || normB.contains(normA)
                || normA.contains("chest") && normB.contains("thorax")
                || normA.contains("thorax") && normB.contains("chest");
    }
}